package de.raindancer.modules.dnd.service;

import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;
import de.raindancer.modules.dnd.model.MagicItem;
import de.raindancer.modules.dnd.model.Spell;
import de.raindancer.modules.dnd.rules.ItemRule;
import de.raindancer.modules.dnd.store.ItemTags;
import de.raindancer.modules.dnd.util.Living;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;

/** Using magic items: drinking, reading, zapping, opening — and what the worn ones are worth. */
public final class ItemService implements IDndService {

    /** Reading a scroll or zapping a wand goes through casting, which knows about turns and targets. */
    @FunctionalInterface
    public interface ItemCaster {
        boolean cast(Player player, Hero hero, Spell spell, int slotLevel, int dc, int attack);
    }

    private final Messages messages;
    private final Effects effects;
    private final HeroService heroes;
    private final ItemForge forge;
    private final ItemRule rule;
    private final SpellCues cues;
    private final RandomGenerator random;
    private final LongSupplier clock;
    private volatile ItemCaster caster = (player, hero, spell, slot, dc, attack) -> false;
    private volatile DndSettings settings = DndSettings.DEFAULTS;

    public ItemService(Messages messages, Effects effects, HeroService heroes, ItemForge forge, ItemRule rule,
                       SpellCues cues, RandomGenerator random, LongSupplier clock) {
        this.messages = messages;
        this.effects = effects;
        this.heroes = heroes;
        this.forge = forge;
        this.rule = rule;
        this.cues = cues;
        this.random = random;
        this.clock = clock;
    }

    public void castWith(ItemCaster how) {
        this.caster = how;
    }

    @Override
    public void settings(DndSettings updated) {
        this.settings = updated == null ? DndSettings.DEFAULTS : updated;
    }

    /** A potion has been drunk: what it does beside being emptied. */
    public void drink(Player player, MagicItem item) {
        switch (item) {
            case POTION_OF_HEALING, POTION_OF_GREATER_HEALING, POTION_OF_SUPERIOR_HEALING, POTION_OF_SUPREME_HEALING -> {
                int healed = item.amount().roll(random);
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

    /** Reads the scroll in hand. One leaves the stack only when the spell went off. */
    public void read(Player player, ItemStack scroll) {
        Optional<Spell> spell = ItemTags.spellOf(scroll);
        Optional<Hero> hero = heroes.of(player.getUniqueId());
        if (spell.isEmpty()) {
            return;
        }
        if (hero.isEmpty() || !rule.mayRead(hero.get(), spell.get())) {
            messages.send(player, "dnd.item.cannot-read", "spell", spell.get().title());
            effects.play(player.getUniqueId(), Cues.NO);
            return;
        }
        int level = spell.get().level();
        if (caster.cast(player, hero.get(), spell.get(), level, rule.scrollDc(level), rule.scrollAttack(level))) {
            scroll.setAmount(scroll.getAmount() - 1);
            messages.send(player, "dnd.item.scroll-read", "spell", spell.get().title());
        }
    }

    /** Zaps the wand in hand: one charge, or three for a stronger volley. */
    public void zap(Player player, ItemStack wand, boolean three) {
        long now = clock.getAsLong();
        int stored = ItemTags.chargesOf(wand);
        long chargedAt = ItemTags.chargedAtOf(wand);
        int charges = rule.charges(stored, chargedAt, now);
        int spending = three ? 3 : 1;
        if (charges < spending) {
            messages.send(player, "dnd.item.wand-empty", "charges", String.valueOf(charges));
            effects.play(player.getUniqueId(), Cues.NO);
            forge.charge(wand, charges, rule.chargedAt(stored, chargedAt, now));
            return;
        }
        Hero hero = heroes.of(player.getUniqueId())
                .orElseGet(() -> Hero.fresh(player.getUniqueId(), HeroClass.WIZARD, List.of()));
        if (caster.cast(player, hero, Spell.MAGIC_MISSILE, spending, rule.scrollDc(spending),
                rule.scrollAttack(spending))) {
            forge.charge(wand, charges - spending, rule.chargedAt(stored, chargedAt, now));
        }
    }

    public void openBag(Player player) {
        player.openInventory(player.getEnderChest());
        effects.play(player.getUniqueId(), Cues.OPEN);
    }

    /** What the weapon in hand adds to a blow, 0 for an ordinary one. */
    public int weaponBonus(ItemStack weapon) {
        return ItemTags.itemOf(weapon).filter(MagicItem::isWeapon).map(item -> item.amount().bonus()).orElse(0);
    }

    public boolean holds(Player player, MagicItem item) {
        return ItemTags.itemOf(player.getInventory().getItemInOffHand()).filter(found -> found == item).isPresent();
    }

    public boolean wears(Player player, MagicItem item) {
        return ItemTags.itemOf(player.getInventory().getBoots()).filter(found -> found == item).isPresent();
    }

    /** A Ring of Protection in the off hand: +1 to every save. */
    public int saveBonus(Player player) {
        return holds(player, MagicItem.RING_OF_PROTECTION) ? 1 : 0;
    }
}
