package de.raindancer.modules.dnd.service;

import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.model.Hero;
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
        boolean active = hero.isPresent() && settings.worksIn(player.getWorld().getName());
        if (!active) {
            set(player, Attribute.MAX_HEALTH, HEALTH, 0, AttributeModifier.Operation.ADD_NUMBER);
            set(player, Attribute.ARMOR, ARMOUR, 0, AttributeModifier.Operation.ADD_NUMBER);
            set(player, Attribute.MOVEMENT_SPEED, SPEED, 0, AttributeModifier.Operation.ADD_SCALAR);
            clampHealth(player);
            return;
        }
        Hero now = hero.get();
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

    /** Takes off every modifier a spell lent, for a long rest. */
    public void endLoans(LivingEntity entity) {
        lend(entity, Attribute.ARMOR, MAGE_ARMOR, 0);
        lend(entity, Attribute.MAX_HEALTH, AID, 0);
        lend(entity, Attribute.ARMOR, HASTE, 0);
    }

    /** Everything this module ever put on a player, gone — for the module stopping. */
    public void strip(Player player) {
        refresh(player, Optional.empty());
        endLoans(player);
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
