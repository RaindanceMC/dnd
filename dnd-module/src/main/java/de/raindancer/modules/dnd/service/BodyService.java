package de.raindancer.modules.dnd.service;

import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.model.Ability;
import de.raindancer.modules.dnd.model.AbilityScores;
import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.MagicItem;
import de.raindancer.modules.dnd.store.ItemTags;
import de.raindancer.modules.dnd.rules.HealthRule;
import de.raindancer.modules.dnd.rules.MartialRule;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * What a class does to the body: more health per level, armour from a fighting style or unarmoured toughness, a
 * barbarian's speed — and what a spell lends for a while.
 *
 * <p>Every one is a <em>transient</em> modifier under this module's own key, so none is ever written into the
 * player's file: leaving, a crash or uninstalling the plugin leaves a vanilla player behind. They are put back on
 * every join and every change instead.
 */
public final class BodyService implements IDndService {

    public static final String NAMESPACE = "rains_dnd";

    static final NamespacedKey HEALTH = new NamespacedKey(NAMESPACE, "class_health");
    static final NamespacedKey ARMOUR = new NamespacedKey(NAMESPACE, "class_armour");
    static final NamespacedKey SPEED = new NamespacedKey(NAMESPACE, "class_speed");
    public static final NamespacedKey MAGE_ARMOR = new NamespacedKey(NAMESPACE, "mage_armor");
    public static final NamespacedKey AID = new NamespacedKey(NAMESPACE, "aid");
    public static final NamespacedKey HASTE = new NamespacedKey(NAMESPACE, "haste");
    static final NamespacedKey ITEM_ARMOUR = new NamespacedKey(NAMESPACE, "item_armour");
    static final NamespacedKey ITEM_SPEED = new NamespacedKey(NAMESPACE, "item_speed");
    static final NamespacedKey ITEM_JUMP = new NamespacedKey(NAMESPACE, "item_jump");
    static final NamespacedKey CLASS_JUMP = new NamespacedKey(NAMESPACE, "class_jump");
    static final NamespacedKey CLASS_FALL = new NamespacedKey(NAMESPACE, "class_fall");

    /** A Boots of Striding's jump: about three times the height. */
    private static final double STRIDING_JUMP = 0.31;

    private final HealthRule health;
    private final MartialRule martial;
    private volatile DndSettings settings = DndSettings.DEFAULTS;

    public BodyService(HealthRule health, MartialRule martial) {
        this.health = health;
        this.martial = martial;
    }

    @Override
    public void settings(DndSettings updated) {
        this.settings = updated == null ? DndSettings.DEFAULTS : updated;
    }

    /** Puts the class's modifiers on, or takes them off when there is no hero or the world has no adventurers. */
    public void refresh(Player player, Optional<Hero> hero) {
        boolean here = settings.worksIn(player.getWorld().getName());
        boolean ring = here && isItem(player.getInventory().getItemInOffHand(), MagicItem.RING_OF_PROTECTION);
        boolean amulet = here && isItem(player.getInventory().getItemInOffHand(), MagicItem.AMULET_OF_HEALTH);
        boolean boots = here && isItem(player.getInventory().getBoots(), MagicItem.BOOTS_OF_STRIDING);
        set(player, Attribute.ARMOR, ITEM_ARMOUR, ring ? 2 : 0, AttributeModifier.Operation.ADD_NUMBER);
        set(player, Attribute.MOVEMENT_SPEED, ITEM_SPEED, boots ? 0.1 : 0, AttributeModifier.Operation.ADD_SCALAR);
        set(player, Attribute.JUMP_STRENGTH, ITEM_JUMP, boots ? STRIDING_JUMP : 0,
                AttributeModifier.Operation.ADD_NUMBER);
        boolean active = hero.isPresent() && here;
        boolean climber = active && Feature.SECOND_STORY_WORK.has(hero.get());
        set(player, Attribute.JUMP_STRENGTH, CLASS_JUMP, climber ? 0.1 : 0, AttributeModifier.Operation.ADD_NUMBER);
        set(player, Attribute.SAFE_FALL_DISTANCE, CLASS_FALL, climber ? 3 : 0, AttributeModifier.Operation.ADD_NUMBER);
        if (!active) {
            set(player, Attribute.MAX_HEALTH, HEALTH, amulet ? 6 : 0, AttributeModifier.Operation.ADD_NUMBER);
            set(player, Attribute.ARMOR, ARMOUR, 0, AttributeModifier.Operation.ADD_NUMBER);
            set(player, Attribute.MOVEMENT_SPEED, SPEED, 0, AttributeModifier.Operation.ADD_SCALAR);
            clampHealth(player);
            return;
        }
        Hero now = amulet ? withConstitution(hero.get(), 19) : hero.get();
        set(player, Attribute.MAX_HEALTH, HEALTH, health.bonusHealth(now, settings.healthPercent()),
                AttributeModifier.Operation.ADD_NUMBER);
        ItemStack chest = player.getInventory().getChestplate();
        boolean wearsChestplate = chest != null && !chest.isEmpty();
        set(player, Attribute.ARMOR, ARMOUR, martial.armourBonus(now, wearsChestplate),
                AttributeModifier.Operation.ADD_NUMBER);
        set(player, Attribute.MOVEMENT_SPEED, SPEED, martial.speedBonus(now), AttributeModifier.Operation.ADD_SCALAR);
        clampHealth(player);
    }

    /** Lends a modifier under this key, replacing any earlier one; 0 takes it away. */
    public void lend(LivingEntity entity, Attribute attribute, NamespacedKey key, double amount) {
        set(entity, attribute, key, amount, AttributeModifier.Operation.ADD_NUMBER);
        clampHealth(entity);
    }

    public boolean has(LivingEntity entity, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance != null && instance.getModifier(key) != null;
    }

    private static boolean isItem(ItemStack stack, MagicItem wanted) {
        return ItemTags.itemOf(stack).filter(found -> found == wanted).isPresent();
    }

    /** The hero as an Amulet of Health has them: Constitution at least {@code score}. */
    private static Hero withConstitution(Hero hero, int score) {
        java.util.Map<Ability, Integer> scores = hero.scores().asMap();
        scores.put(Ability.CONSTITUTION, Math.max(score, scores.get(Ability.CONSTITUTION)));
        return hero.withScores(AbilityScores.of(scores), hero.improvements());
    }

    /** Takes off every modifier a spell lent, for a long rest. */
    public void endLoans(LivingEntity entity) {
        lend(entity, Attribute.ARMOR, MAGE_ARMOR, 0);
        lend(entity, Attribute.MAX_HEALTH, AID, 0);
        lend(entity, Attribute.ARMOR, HASTE, 0);
    }

    /** Everything this module ever put on a player, gone — for the module stopping. */
    public void strip(Player player) {
        for (Attribute attribute : java.util.List.of(Attribute.MAX_HEALTH, Attribute.ARMOR, Attribute.MOVEMENT_SPEED,
                Attribute.JUMP_STRENGTH, Attribute.SAFE_FALL_DISTANCE, Attribute.SCALE, Attribute.GRAVITY)) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            for (AttributeModifier modifier : java.util.List.copyOf(instance.getModifiers())) {
                if (NAMESPACE.equals(modifier.getKey().getNamespace())) {
                    instance.removeModifier(modifier);
                }
            }
        }
        clampHealth(player);
    }

    private static void set(LivingEntity entity, Attribute attribute, NamespacedKey key, double amount,
                            AttributeModifier.Operation operation) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(key);
        if (current != null) {
            if (current.getAmount() == amount && current.getOperation() == operation) {
                return;
            }
            instance.removeModifier(key);
        }
        if (amount != 0) {
            instance.addTransientModifier(new AttributeModifier(key, amount, operation, EquipmentSlotGroup.ANY));
        }
    }

    private static void clampHealth(LivingEntity entity) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && entity.getHealth() > max.getValue()) {
            entity.setHealth(max.getValue());
        }
    }
}
