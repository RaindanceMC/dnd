package de.raindancer.modules.dnd.service;

import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.model.MagicItem;
import de.raindancer.modules.dnd.model.Rarity;
import de.raindancer.modules.dnd.rules.LootRule;
import de.raindancer.modules.dnd.rules.MobRule;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Magic items dropped by the monsters adventurers defeat. */
public final class LootService implements IDndService {

    private static final List<Material> UNCOMMON_WEAPONS = List.of(Material.IRON_SWORD, Material.IRON_AXE,
            Material.BOW);
    private static final List<Material> RARE_WEAPONS = List.of(Material.DIAMOND_SWORD, Material.DIAMOND_AXE,
            Material.BOW, Material.CROSSBOW);
    private static final List<Material> VERY_RARE_WEAPONS = List.of(Material.NETHERITE_SWORD,
            Material.NETHERITE_AXE, Material.BOW, Material.TRIDENT);

    private final Messages messages;
    private final Effects effects;
    private final ItemForge forge;
    private final LootRule rule;
    private final MobRule mobs;
    private final RandomGenerator random;
    private volatile DndSettings settings = DndSettings.DEFAULTS;
    /** When each player last found something, for the hourly cap. */
    private final java.util.Map<java.util.UUID, java.util.Deque<Long>> drops =
            new java.util.concurrent.ConcurrentHashMap<>();

    public LootService(Messages messages, Effects effects, ItemForge forge, LootRule rule, MobRule mobs,
                       RandomGenerator random) {
        this.messages = messages;
        this.effects = effects;
        this.forge = forge;
        this.rule = rule;
        this.mobs = mobs;
        this.random = random;
    }

    @Override
    public void settings(DndSettings updated) {
        this.settings = updated == null ? DndSettings.DEFAULTS : updated;
    }

    /**
     * What this kill drops, if anything. The killer is told when something does.
     *
     * @param morePercent the killer's Treasure Sense and its kin
     */
    public Optional<ItemStack> roll(Player killer, LivingEntity victim, double morePercent) {
        // A spawner's mobs, a player who is away, a fourth drop within the hour: the shape of a mob farm.
        if (!settings.worksIn(victim.getWorld().getName())
                || victim.getEntitySpawnReason() == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER
                || de.raindancer.core.social.presence.Away.isAway(killer.getUniqueId())) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        java.util.Deque<Long> recent = drops.computeIfAbsent(killer.getUniqueId(),
                id -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        recent.removeIf(at -> now - at >= 3_600_000L);
        if (!rule.mayDrop(recent, now)) {
            return Optional.empty();
        }
        int xp = mobs.of(victim.getType()).xp();
        if (random.nextDouble() >= rule.withLuck(rule.dropChance(xp, settings.lootPercent()), morePercent)) {
            return Optional.empty();
        }
        recent.add(now);
        Rarity rarity = rule.rarity(xp, random.nextDouble());
        LootRule.Drop drop = rule.item(rarity, random);
        ItemStack stack = forge.make(drop.item(), drop.scroll(), drop.tier(), weaponBase(drop.item()));
        messages.send(killer, "dnd.loot", "item", drop.scroll() == null ? drop.item().title()
                : "Spell Scroll of " + drop.scroll().title(), "rarity", rarity.title().toLowerCase(java.util.Locale.ROOT));
        effects.play(killer.getUniqueId(), Cues.REWARD);
        return Optional.of(stack);
    }

    public void forget(java.util.UUID player) {
        drops.remove(player);
    }

    private Material weaponBase(MagicItem item) {
        List<Material> bases = switch (item) {
            case WEAPON_PLUS_ONE -> UNCOMMON_WEAPONS;
            case WEAPON_PLUS_TWO -> RARE_WEAPONS;
            case WEAPON_PLUS_THREE -> VERY_RARE_WEAPONS;
            default -> List.of(item.material());
        };
        return bases.get(random.nextInt(bases.size()));
    }
}
