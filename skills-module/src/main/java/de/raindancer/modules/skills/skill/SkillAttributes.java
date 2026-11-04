package de.raindancer.modules.skills.skill;

import de.raindancer.modules.skills.service.BodyService;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * The attributes these skills lend: transient modifiers under {@link BodyService#NAMESPACE}, so they are never
 * written into a player's file and {@code BodyService.strip} takes them off with the rest when the module stops.
 */
public final class SkillAttributes {

    /** One modifier: its key, the attribute, how it adds, and how much a player gets of it now. */
    public record Lent(String key, String attribute, AttributeModifier.Operation operation,
                       ToDoubleFunction<Player> amount) {
    }

    private final List<Lent> lent;

    public SkillAttributes(List<Lent> lent) {
        this.lent = List.copyOf(lent);
    }

    /** A tree's attribute effects, each as much as the player's nodes add up to. */
    public static List<Lent> of(List<? extends AttributeEffect> effects, SkillValues values) {
        List<Lent> lent = new ArrayList<>();
        for (AttributeEffect effect : effects) {
            lent.add(new Lent("tree_" + effect.id(), effect.attribute(), effect.multiplies()
                    ? AttributeModifier.Operation.ADD_SCALAR : AttributeModifier.Operation.ADD_NUMBER,
                    player -> values.value(player, effect) * effect.scale()));
        }
        return lent;
    }

    public void refresh(Player player) {
        for (Lent one : lent) {
            set(player, one.attribute(), new NamespacedKey(BodyService.NAMESPACE, one.key()),
                    one.amount().applyAsDouble(player), one.operation());
        }
    }

    /** Puts a modifier on — or takes it off, for 0 — on any living thing: a horse is ridden faster too. */
    public static void set(LivingEntity entity, String attributeKey, NamespacedKey key, double amount,
                           AttributeModifier.Operation operation) {
        Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(attributeKey));
        AttributeInstance instance = attribute == null ? null : entity.getAttribute(attribute);
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
}
