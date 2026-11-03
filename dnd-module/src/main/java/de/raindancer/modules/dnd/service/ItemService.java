package de.raindancer.modules.dnd.service;

import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.model.MagicItem;
import de.raindancer.modules.dnd.model.Perk;
import de.raindancer.modules.dnd.model.Scroll;
import de.raindancer.modules.dnd.rules.ItemRule;
import de.raindancer.modules.dnd.store.ItemTags;
import de.raindancer.modules.dnd.util.Living;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;

/** Using magic items: drinking, reading, zapping, opening — with what the skill tree adds to each. */
public final class ItemService implements IDndService {

    private final Messages messages;
    private final Effects effects;
    private final CombatService combat;
    private final ItemForge forge;
    private final ItemRule rule;
    private final ScrollService scrolls;
    private final SpellCues cues;
    private final RandomGenerator random;
    private final LongSupplier clock;
    private volatile DndSettings settings = DndSettings.DEFAULTS;

    public ItemService(Messages messages, Effects effects, CombatService combat, ItemForge forge, ItemRule rule,
                       ScrollService scrolls, SpellCues cues, RandomGenerator random, LongSupplier clock) {
        this.messages = messages;
        this.effects = effects;
        this.combat = combat;
        this.forge = forge;
        this.rule = rule;
        this.scrolls = scrolls;
        this.cues = cues;
        this.random = random;
        this.clock = clock;
    }

    @Override
    public void settings(DndSettings updated) {
        this.settings = updated == null ? DndSettings.DEFAULTS : updated;
    }

    private double percent(Player player, Perk perk) {
        return combat.chance(player, perk) * 100;
    }

    /** A potion has been drunk: what it does beside being emptied. */
    public void drink(Player player, MagicItem item) {
        switch (item) {
            case POTION_OF_HEALING, POTION_OF_GREATER_HEALING, POTION_OF_SUPERIOR_HEALING, POTION_OF_SUPREME_HEALING -> {
                int healed = (int) Math.round(item.amount().roll(random) * (100 + percent(player, Perk.POTION_POWER))
                        / 100.0);
                player.setHealth(Math.min(TargetService.maxHealth(player), player.getHealth() + healed));
                cues.at(TargetService.middleOf(player), de.raindancer.modules.dnd.model.DamageKind.HEALING);
                messages.send(player, "dnd.item.healed", "health", String.valueOf(healed));
            }
            case POTION_OF_HEROISM -> {
                player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), item.amount().bonus()));
                Living.give(player, PotionEffectType.STRENGTH, 60, 0);
                messages.send(player, "dnd.item.heroism");
            }
            case POTION_OF_SPEED -> {
                Living.give(player, PotionEffectType.SPEED, 60, 1);
                Living.give(player, PotionEffectType.HASTE, 60, 1);
                Living.give(player, PotionEffectType.RESISTANCE, 60, 0);
                messages.send(player, "dnd.item.speed");
            }
            default -> {
            }
        }
    }

    /** Reads the scroll in hand. One leaves the stack only when it went off — and Scribe may keep it anyway. */
    public void read(Player player, ItemStack scroll) {
        Optional<Scroll> kind = ItemTags.scrollOf(scroll);
        if (kind.isEmpty()) {
            return;
        }
        int tier = ItemTags.tierOf(scroll);
        if (!scrolls.cast(player, kind.get(), tier, percent(player, Perk.SCROLL_POWER), 3)) {
            messages.send(player, "dnd.item.nothing-there", "scroll", kind.get().title());
            effects.play(player.getUniqueId(), Cues.NO);
            return;
        }
        boolean kept = ThreadLocalRandom.current().nextDouble() * 100 < percent(player, Perk.SCROLL_SAVE);
        if (!kept) {
            scroll.setAmount(scroll.getAmount() - 1);
        }
        messages.send(player, kept ? "dnd.item.scroll-kept" : "dnd.item.scroll-read", "scroll", kind.get().title());
    }

    /** Zaps the wand in hand: one charge, or three for a stronger volley. */
    public void zap(Player player, ItemStack wand, boolean three) {
        long now = clock.getAsLong();
        long every = rule.rechargeMillis((int) Math.round(percent(player, Perk.WAND_RECHARGE)));
        int stored = ItemTags.chargesOf(wand);
        long chargedAt = ItemTags.chargedAtOf(wand);
        int charges = rule.charges(stored, chargedAt, now, every);
        int spending = three ? 3 : 1;
        if (charges < spending) {
            messages.send(player, "dnd.item.wand-empty", "charges", String.valueOf(charges));
            effects.play(player.getUniqueId(), Cues.NO);
            forge.charge(wand, charges, rule.chargedAt(stored, chargedAt, now, every));
            return;
        }
        if (scrolls.cast(player, Scroll.MAGIC_MISSILE, 1, percent(player, Perk.SCROLL_POWER), 3 * spending)) {
            forge.charge(wand, charges - spending, rule.chargedAt(stored, chargedAt, now, every));
        } else {
            messages.send(player, "dnd.item.nothing-there", "scroll", Scroll.MAGIC_MISSILE.title());
        }
    }

    public void openBag(Player player) {
        player.openInventory(player.getEnderChest());
        effects.play(player.getUniqueId(), Cues.OPEN);
    }
}
