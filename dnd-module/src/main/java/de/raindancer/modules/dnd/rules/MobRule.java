package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.MobProfile;
import org.bukkit.entity.EntityType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * What a creature counts as: an armour class, a saving throw bonus and the experience it is worth, after the
 * challenge rating its nearest relative at the table would have.
 */
public final class MobRule implements IDndRule {

    private static final Set<EntityType> UNDEAD = Set.of(EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER,
            EntityType.HUSK, EntityType.DROWNED, EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED,
            EntityType.WITHER_SKELETON, EntityType.ZOMBIFIED_PIGLIN, EntityType.ZOGLIN, EntityType.PHANTOM,
            EntityType.WITHER, EntityType.SKELETON_HORSE, EntityType.ZOMBIE_HORSE);

    private static final Set<EntityType> HUMANOID = Set.of(EntityType.PLAYER, EntityType.VILLAGER,
            EntityType.WANDERING_TRADER, EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.EVOKER,
            EntityType.ILLUSIONER, EntityType.WITCH, EntityType.PIGLIN, EntityType.PIGLIN_BRUTE);

    private final Map<EntityType, MobProfile> table = new EnumMap<>(EntityType.class);

    public MobRule() {
        tier(12, 0, 50, EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.HUSK, EntityType.DROWNED,
                EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED, EntityType.SPIDER, EntityType.CAVE_SPIDER,
                EntityType.SILVERFISH, EntityType.ENDERMITE, EntityType.SLIME, EntityType.MAGMA_CUBE,
                EntityType.PHANTOM, EntityType.PILLAGER, EntityType.ZOMBIFIED_PIGLIN, EntityType.PIGLIN,
                EntityType.HOGLIN, EntityType.ZOGLIN, EntityType.VEX, EntityType.GUARDIAN, EntityType.SHULKER,
                EntityType.BREEZE);
        tier(13, 1, 100, EntityType.CREEPER, EntityType.WITCH, EntityType.VINDICATOR, EntityType.GHAST,
                EntityType.ENDERMAN, EntityType.CREAKING);
        tier(15, 3, 200, EntityType.BLAZE, EntityType.WITHER_SKELETON, EntityType.PIGLIN_BRUTE,
                EntityType.ILLUSIONER);
        tier(16, 4, 450, EntityType.EVOKER, EntityType.RAVAGER, EntityType.IRON_GOLEM);
        tier(17, 6, 1_800, EntityType.ELDER_GUARDIAN);
        tier(18, 7, 5_000, EntityType.WARDEN);
        tier(19, 8, 10_000, EntityType.WITHER);
        tier(20, 9, 20_000, EntityType.ENDER_DRAGON);
    }

    private void tier(int armourClass, int saveBonus, int xp, EntityType... types) {
        for (EntityType type : types) {
            table.put(type, new MobProfile(armourClass, saveBonus, xp, UNDEAD.contains(type),
                    HUMANOID.contains(type)));
        }
    }

    public MobProfile of(EntityType type) {
        MobProfile known = table.get(type);
        if (known != null) {
            return known;
        }
        return new MobProfile(10, 0, 0, UNDEAD.contains(type), HUMANOID.contains(type));
    }

    @Override
    public String describe() {
        return "a creature's armour class, saving throws, experience and kind";
    }
}
