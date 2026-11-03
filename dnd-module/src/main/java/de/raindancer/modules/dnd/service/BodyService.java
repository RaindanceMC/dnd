package de.raindancer.modules.dnd.service;

import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.MagicItem;
import de.raindancer.modules.dnd.model.Perk;
import de.raindancer.modules.dnd.rules.PerkRule;
import de.raindancer.modules.dnd.store.ItemTags;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;

/**
 * What the skill tree and the worn magic items do to the body: hearts, armour, speed, reach and the rest of the
 * tree's attribute effects, an Amulet of Health's hearts, a Ring of Protection's armour, a Boots of Striding's legs.
 *
 * <p>Every one is a <em>transient</em> modifier under this module's own key, so none is ever written into the
 * player's file: leaving, a crash or uninstalling the plugin leaves a vanilla player behind. They are put back on
 * every join and every change instead.
 */
public final class BodyService implements IDndService {

    public static final String NAMESPACE = "rains_dnd";

    static final NamespacedKey ITEM_ARMOUR = new NamespacedKey(NAMESPACE, "item_armour");
    static final NamespacedKey ITEM_HEALTH = new NamespacedKey(NAMESPACE, "item_health");
    static final NamespacedKey ITEM_SPEED = new NamespacedKey(NAMESPACE, "item_speed");
    static final NamespacedKey ITEM_JUMP = new NamespacedKey(NAMESPACE, "item_jump");

    /** A Boots of Striding's jump: about three times the height. */
    private static final double STRIDING_JUMP = 0.31;

    private final PerkRule perks;
    private volatile DndSettings settings = DndSettings.DEFAULTS;

    public BodyService(PerkRule perks) {
        this.perks = perks;
    }

    @Override
    public void settings(DndSettings updated) {
        this.settings = updated == null ? DndSettings.DEFAULTS : updated;
    }

    static NamespacedKey treeKey(Perk perk) {
        return new NamespacedKey(NAMESPACE, "tree_" + perk.id());
    }

    /** Puts the tree's and the items' modifiers on, or takes them off outside the adventurers' worlds. */
    public void refresh(Player player, Optional<Hero> hero) {
        boolean here = settings.worksIn(player.getWorld().getName());
        boolean ring = here && isItem(player.getInventory().getItemInOffHand(), MagicItem.RING_OF_PROTECTION);
        boolean amulet = here && isItem(player.getInventory().getItemInOffHand(), MagicItem.AMULET_OF_HEALTH);
        boolean boots = here && isItem(player.getInventory().getBoots(), MagicItem.BOOTS_OF_STRIDING);
        set(player, Attribute.ARMOR, ITEM_ARMOUR, ring ? 2 : 0, AttributeModifier.Operation.ADD_NUMBER);
        set(player, Attribute.MAX_HEALTH, ITEM_HEALTH,
                amulet ? MagicItem.AMULET_OF_HEALTH.amount().bonus() * 2.0 : 0, AttributeModifier.Operation.ADD_NUMBER);
        set(player, Attribute.MOVEMENT_SPEED, ITEM_SPEED, boots ? 0.1 : 0, AttributeModifier.Operation.ADD_SCALAR);
        set(player, Attribute.JUMP_STRENGTH, ITEM_JUMP, boots ? STRIDING_JUMP : 0,
                AttributeModifier.Operation.ADD_NUMBER);
        Map<Perk, Double> values = here && hero.isPresent() ? perks.values(hero.get()) : Map.of();
        for (Perk perk : Perk.values()) {
            Attribute attribute = attribute(perk);
            if (attribute == null) {
                continue;
            }
            double amount = values.getOrDefault(perk, 0.0) * perk.scale();
            set(player, attribute, treeKey(perk), amount, perk.multiplies()
                    ? AttributeModifier.Operation.ADD_SCALAR : AttributeModifier.Operation.ADD_NUMBER);
        }
        clampHealth(player);
    }

    private static Attribute attribute(Perk perk) {
        return perk.attribute() == null ? null : Registry.ATTRIBUTE.get(NamespacedKey.minecraft(perk.attribute()));
    }

    private static boolean isItem(ItemStack stack, MagicItem wanted) {
        return ItemTags.itemOf(stack).filter(found -> found == wanted).isPresent();
    }

    /** Everything this module ever put on a player, gone — for the module stopping. */
    public void strip(Player player) {
        for (Attribute attribute : Registry.ATTRIBUTE) {
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
