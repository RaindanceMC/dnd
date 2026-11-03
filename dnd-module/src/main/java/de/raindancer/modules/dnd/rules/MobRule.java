package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.MobProfile;
import org.bukkit.entity.EntityType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * What a creature is worth to an adventurer — the experience its nearest relative at the table would give — and
 * whether it is undead.
 */
public final class MobRule implements IDndRule {

    private static final Set<EntityType> UNDEAD = Set.of(EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER,
            EntityType.HUSK, EntityType.DROWNED, EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED,
            EntityType.WITHER_SKELETON, EntityType.ZOMBIFIED_PIGLIN, EntityType.ZOGLIN, EntityType.PHANTOM,
            EntityType.WITHER, EntityType.SKELETON_HORSE, EntityType.ZOMBIE_HORSE);


    private final Map<EntityType, MobProfile> table = new EnumMap<>(EntityType.class);

    public MobRule() {
        tier(50, EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.HUSK, EntityType.DROWNED,
                EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED, EntityType.SPIDER, EntityType.CAVE_SPIDER,
                EntityType.SILVERFISH, EntityType.ENDERMITE, EntityType.SLIME, EntityType.MAGMA_CUBE,
                EntityType.PHANTOM, EntityType.PILLAGER, EntityType.ZOMBIFIED_PIGLIN, EntityType.PIGLIN,
                EntityType.HOGLIN, EntityType.ZOGLIN, EntityType.VEX, EntityType.GUARDIAN, EntityType.SHULKER,
                EntityType.BREEZE);
        tier(100, EntityType.CREEPER, EntityType.WITCH, EntityType.VINDICATOR, EntityType.GHAST,
                EntityType.ENDERMAN, EntityType.CREAKING);
        tier(200, EntityType.BLAZE, EntityType.WITHER_SKELETON, EntityType.PIGLIN_BRUTE,
                EntityType.ILLUSIONER);
        tier(450, EntityType.EVOKER, EntityType.RAVAGER, EntityType.IRON_GOLEM);
        tier(1_800, EntityType.ELDER_GUARDIAN);
        tier(5_000, EntityType.WARDEN);
        tier(10_000, EntityType.WITHER);
        tier(20_000, EntityType.ENDER_DRAGON);
    }

    private void tier(int xp, EntityType... types) {
        for (EntityType type : types) {
            table.put(type, new MobProfile(xp, UNDEAD.contains(type)));
        }
    }

    public MobProfile of(EntityType type) {
        MobProfile known = table.get(type);
        if (known != null) {
            return known;
        }
        return new MobProfile(0, UNDEAD.contains(type));
    }

    @Override
    public String describe() {
        return "what a creature is worth, and whether it is undead";
    }
}
