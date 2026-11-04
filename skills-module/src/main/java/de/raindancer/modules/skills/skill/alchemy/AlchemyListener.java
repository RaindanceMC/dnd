package de.raindancer.modules.skills.skill.alchemy;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.modules.api.ModuleContext;
import de.raindancer.modules.skills.SkillsServices;
import de.raindancer.modules.skills.listener.ISkillsListener;
import de.raindancer.modules.skills.skill.SkillValues;
import de.raindancer.modules.skills.skill.Workers;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Alchemy at work, at the stand the player last opened (as Core counts a brew) and wherever a marked potion is drunk
 * or thrown. A potion keeps what its alchemist put in ({@link AlchemyRule.Mark}) in its own data, so it works for
 * whoever drinks it; brewing it on makes a new potion, marked again by whoever brews that. Nothing here turns a
 * potion back into an ingredient, so a spare potion or a saved ingredient is never a loop: at most 40% is saved.
 */
public final class AlchemyListener implements ISkillsListener {

    private static final Set<Material> POTIONS = Set.of(Material.POTION, Material.SPLASH_POTION,
            Material.LINGERING_POTION);
    private static final int MOST_FUEL = 20;

    private final Plugin plugin;
    private final SkillValues values;
    private final AlchemyRule rule = new AlchemyRule();
    private final Workers brewers = new Workers();
    private final NamespacedKey longerKey;
    private final NamespacedKey strongerKey;

    AlchemyListener(Plugin plugin, SkillValues values) {
        this.plugin = plugin;
        this.values = values;
        this.longerKey = new NamespacedKey(plugin, "brew_longer");
        this.strongerKey = new NamespacedKey(plugin, "brew_stronger");
    }

    public static void start(ModuleContext context, SkillsServices services) {
        context.listener(new AlchemyListener(context.plugin(), new SkillValues(services)));
    }

    // ------------------------------------------------------------------ the stand

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory() instanceof BrewerInventory stand && stand.getLocation() != null) {
            brewers.worked(stand.getLocation(), event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStart(BrewingStartEvent event) {
        Player brewer = brewers.of(plugin.getServer(), event.getBlock());
        if (brewer == null) {
            return;
        }
        int ticks = rule.brewTime(event.getRecipeBrewTime(), values.value(brewer, AlchemyEffect.SPEED));
        event.setRecipeBrewTime(ticks);
        event.setBrewingTime(ticks);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        Block block = event.getBlock();
        Player brewer = brewers.of(plugin.getServer(), block);
        if (brewer == null) {
            return;
        }
        List<ItemStack> results = event.getResults();
        boolean[] bottles = new boolean[results.size()];
        double longer = values.value(brewer, AlchemyEffect.DURATION);
        double stronger = values.value(brewer, AlchemyEffect.POTENCY);
        for (int slot = 0; slot < results.size(); slot++) {
            ItemStack potion = results.get(slot);
            bottles[slot] = potion != null && POTIONS.contains(potion.getType());
            if (bottles[slot]) {
                mark(potion, rule.mark(longer, stronger, ThreadLocalRandom.current().nextDouble()));
            }
        }
        int empty = rule.emptySlot(bottles);
        if (empty >= 0 && values.roll(brewer, AlchemyEffect.EXTRA)) {
            results.set(empty, results.get(rule.fullSlot(bottles)).clone());
        }
        ItemStack ingredient = event.getContents().getIngredient();
        Material used = ingredient == null || ingredient.isEmpty() ? null : ingredient.getType();
        boolean thrift = used != null && values.roll(brewer, AlchemyEffect.THRIFT);
        boolean catalyst = values.has(brewer, AlchemyEffect.CATALYST);
        if (!thrift && !catalyst) {
            return;
        }
        // The game takes the ingredient after this event; it is given back once that is done.
        Scheduling.regionLater(plugin, block.getLocation(), 1, () -> {
            if (!(block.getState(false) instanceof BrewingStand stand)) {
                return;
            }
            if (catalyst) {
                stand.setFuelLevel(Math.min(MOST_FUEL, stand.getFuelLevel() + 1));
            }
            if (thrift) {
                BrewerInventory inventory = stand.getInventory();
                ItemStack now = inventory.getIngredient();
                if (now == null || now.isEmpty()) {
                    inventory.setIngredient(new ItemStack(used));
                } else if (now.getType() == used && now.getAmount() < now.getMaxStackSize()) {
                    now.setAmount(now.getAmount() + 1);
                    inventory.setIngredient(now);
                } else {
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 1, 0.5), new ItemStack(used));
                }
            }
        });
    }

    private void mark(ItemStack potion, AlchemyRule.Mark mark) {
        if (mark.plain()) {
            return;
        }
        potion.editMeta(meta -> {
            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(longerKey, PersistentDataType.INTEGER, mark.longer());
            data.set(strongerKey, PersistentDataType.INTEGER, mark.stronger());
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            if (mark.longer() > 0) {
                lore.add(values.services().messages().get("dnd.nature.brew-longer", "percent", mark.longer()));
            }
            if (mark.stronger() > 0) {
                lore.add(values.services().messages().get("dnd.nature.brew-stronger"));
            }
            meta.lore(lore);
        });
    }

    private AlchemyRule.Mark markOf(ItemStack potion) {
        if (potion == null || !POTIONS.contains(potion.getType()) || !potion.hasItemMeta()) {
            return AlchemyRule.Mark.NONE;
        }
        PersistentDataContainer data = potion.getItemMeta().getPersistentDataContainer();
        return new AlchemyRule.Mark(data.getOrDefault(longerKey, PersistentDataType.INTEGER, 0),
                data.getOrDefault(strongerKey, PersistentDataType.INTEGER, 0));
    }

    // ------------------------------------------------------------------ drunk and thrown

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBottomless(PlayerItemConsumeEvent event) {
        ItemStack potion = event.getItem();
        Player drinker = event.getPlayer();
        if (potion.getType() == Material.POTION && values.has(drinker, AlchemyEffect.BOTTOMLESS)
                && ThreadLocalRandom.current().nextDouble() < AlchemyRule.BOTTOMLESS) {
            event.setReplacement(potion.asOne());
            values.notice(drinker, Cues.MAGIC, "dnd.nature.bottomless");
        }
    }

    /** A marked potion drunk: its effects again, longer and stronger, a tick after the game's own. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        ItemStack potion = event.getItem();
        AlchemyRule.Mark mark = markOf(potion);
        if (mark.plain() || !(potion.getItemMeta() instanceof PotionMeta meta)) {
            return;
        }
        List<PotionEffect> effects = meta.getAllEffects();
        Player drinker = event.getPlayer();
        Scheduling.entityLater(plugin, drinker, 1, () -> strengthen(drinker, effects, mark, 1.0));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        AlchemyRule.Mark mark = markOf(event.getPotion().getItem());
        if (mark.plain()) {
            return;
        }
        List<PotionEffect> effects = event.getPotion().getPotionMeta().getAllEffects();
        for (LivingEntity hit : event.getAffectedEntities()) {
            double intensity = event.getIntensity(hit);
            if (intensity > 0) {
                Scheduling.entityLater(plugin, hit, 1, () -> strengthen(hit, effects, mark, intensity));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLinger(LingeringPotionSplashEvent event) {
        AlchemyRule.Mark mark = markOf(event.getEntity().getItem());
        if (mark.plain()) {
            return;
        }
        for (PotionEffect effect : event.getEntity().getPotionMeta().getAllEffects()) {
            event.getAreaEffectCloud().addCustomEffect(better(effect, mark, 1.0), true);
        }
    }

    private void strengthen(LivingEntity target, List<PotionEffect> effects, AlchemyRule.Mark mark, double share) {
        if (!target.isValid()) {
            return;
        }
        for (PotionEffect effect : effects) {
            if (effect.getType().isInstant()) {
                if (mark.stronger() > 0) {
                    effect.getType().createEffect(1, effect.getAmplifier()).apply(target);
                }
            } else {
                target.addPotionEffect(better(effect, mark, share));
            }
        }
    }

    private PotionEffect better(PotionEffect effect, AlchemyRule.Mark mark, double share) {
        int ticks = effect.isInfinite() ? effect.getDuration()
                : (int) Math.round(rule.lasting(effect.getDuration(), mark.longer()) * share);
        return new PotionEffect(effect.getType(), Math.max(1, ticks), effect.getAmplifier() + mark.stronger(),
                effect.isAmbient(), effect.hasParticles(), effect.hasIcon());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    @Override
    public void forget(UUID player) {
        brewers.forget(player);
    }
}
