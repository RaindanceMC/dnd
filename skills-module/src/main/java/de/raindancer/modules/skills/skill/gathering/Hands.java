package de.raindancer.modules.skills.skill.gathering;

import de.raindancer.modules.skills.service.BodyService;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * The tool in a player's hand: its speed lent while it is held, whether it has silk touch, how worn it is. Speed is a
 * transient modifier in {@link BodyService#NAMESPACE}, so nothing is saved into the player and the module stopping
 * takes it off with the rest.
 */
public final class Hands {

    private Hands() {
    }

    public static NamespacedKey key(String name) {
        return new NamespacedKey(BodyService.NAMESPACE, name);
    }

    /** Mining {@code percent} faster — 0 to take it off. */
    public static void speed(Player player, NamespacedKey key, double percent) {
        AttributeInstance instance = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (instance == null) {
            return;
        }
        double amount = percent / 100.0;
        AttributeModifier current = instance.getModifier(key);
        if (current != null) {
            if (current.getAmount() == amount) {
                return;
            }
            instance.removeModifier(key);
        }
        if (amount != 0) {
            instance.addTransientModifier(new AttributeModifier(key, amount,
                    AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
        }
    }

    public static boolean silk(ItemStack tool) {
        return tool != null && tool.containsEnchantment(Enchantment.SILK_TOUCH);
    }

    /** The tool's durability: {@code [damage, max]}, max 0 for something that does not wear. */
    public static int[] wear(ItemStack tool) {
        if (tool == null || !(tool.getItemMeta() instanceof Damageable damageable) || tool.getType().getMaxDurability() <= 0
                || tool.getItemMeta().isUnbreakable()) {
            return new int[] {0, 0};
        }
        int max = damageable.hasMaxDamage() ? damageable.getMaxDamage() : tool.getType().getMaxDurability();
        return new int[] {damageable.getDamage(), max};
    }
}
