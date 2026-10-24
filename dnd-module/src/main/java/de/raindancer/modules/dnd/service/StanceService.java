package de.raindancer.modules.dnd.service;

import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.model.Castable;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.Metamagic;
import de.raindancer.modules.dnd.rules.LoadoutRule;
import de.raindancer.modules.dnd.rules.MetamagicRule;
import de.raindancer.modules.dnd.store.IconTags;
import de.raindancer.modules.dnd.store.StanceStash;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The casting stance: the hotbar becomes the hero's spells and features, and comes back exactly as it was.
 *
 * <h2>Where the real hotbar is meanwhile</h2>
 * On the player, through {@link StanceStash}, written and read back <em>before</em> a single item is replaced — so a
 * crash, a kick or a server stop mid-stance cannot cost an item: the next join finds the stash and puts it back.
 * Every way out of the stance — F again, death, leaving, a world without adventurers, the module stopping — goes
 * through {@link #leave}, and an icon found anywhere outside a stance is removed on sight.
 */
public final class StanceService implements IDndService {

    /** The ninth hotbar slot: the sheet, or a sorcerer's metamagic. */
    public static final int SHEET_SLOT = 8;

    private final Messages messages;
    private final Effects effects;
    private final StanceStash stash;
    private final IconForge icons;
    private final LoadoutRule loadouts;
    private final MetamagicRule metamagic;

    private final Set<UUID> inStance = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Metamagic> armed = new ConcurrentHashMap<>();

    private volatile DndSettings settings = DndSettings.DEFAULTS;

    public StanceService(Messages messages, Effects effects, StanceStash stash, IconForge icons,
                         LoadoutRule loadouts, MetamagicRule metamagic) {
        this.messages = messages;
        this.effects = effects;
        this.stash = stash;
        this.icons = icons;
        this.loadouts = loadouts;
        this.metamagic = metamagic;
    }

    @Override
    public void settings(DndSettings updated) {
        this.settings = updated == null ? DndSettings.DEFAULTS : updated;
    }

    public boolean isIn(UUID player) {
        return inStance.contains(player);
    }

    public Set<UUID> everyone() {
        return Set.copyOf(inStance);
    }

    /** Takes the stance up. False, with the reason told, when it cannot be. */
    public boolean enter(Player player, Hero hero) {
        if (isIn(player.getUniqueId())) {
            return true;
        }
        if (!settings.worksIn(player.getWorld().getName())) {
            messages.send(player, "dnd.stance.not-here");
            effects.play(player.getUniqueId(), Cues.NO);
            return false;
        }
        if (stash.holds(player)) {
            // A hotbar from an earlier stance was never put back. That one comes first, always.
            restore(player);
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] hotbar = new ItemStack[StanceStash.HOTBAR];
        for (int slot = 0; slot < StanceStash.HOTBAR; slot++) {
            hotbar[slot] = inventory.getItem(slot);
        }
        stash.keep(player, hotbar);
        Optional<ItemStack[]> check = stash.read(player);
        if (check.isEmpty() || !same(hotbar, check.get())) {
            stash.drop(player);
            messages.send(player, "dnd.stance.could-not-keep");
            effects.play(player.getUniqueId(), Cues.ERROR);
            return false;
        }
        inStance.add(player.getUniqueId());
        draw(player, hero);
        effects.play(player.getUniqueId(), Cues.ENTER);
        messages.send(player, "dnd.stance.entered");
        return true;
    }

    private static boolean same(ItemStack[] kept, ItemStack[] read) {
        for (int slot = 0; slot < StanceStash.HOTBAR; slot++) {
            ItemStack a = kept[slot] == null || kept[slot].isEmpty() ? null : kept[slot];
            ItemStack b = read[slot] == null || read[slot].isEmpty() ? null : read[slot];
            if (a == null ? b != null : !a.isSimilar(b) || a.getAmount() != b.getAmount()) {
                return false;
            }
        }
        return true;
    }

    /** Leaves the stance and puts the real hotbar back. Safe to call for anybody, any time. */
    public void leave(Player player, boolean quietly) {
        boolean was = inStance.remove(player.getUniqueId());
        restore(player);
        if (was && !quietly) {
            effects.play(player.getUniqueId(), Cues.LEAVE);
            messages.send(player, "dnd.stance.left");
        }
    }

    /** Redraws the icons, after anything about the hero changed. */
    public void redraw(Player player, Hero hero) {
        if (isIn(player.getUniqueId())) {
            draw(player, hero);
        }
    }

    private void draw(Player player, Hero hero) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < Hero.LOADOUT_SIZE; slot++) {
            int index = slot;
            ItemStack icon = loadouts.at(hero, slot)
                    .map(castable -> icons.stanceIcon(castable, hero, index))
                    .orElseGet(() -> icons.emptySlot(index));
            inventory.setItem(slot, icon);
        }
        inventory.setItem(SHEET_SLOT, icons.sheetSlot(hero, metamagic.knows(hero), armed.get(player.getUniqueId())));
    }

    /**
     * Puts the kept hotbar back, removes every icon from everywhere, and hands back anything that somehow ended up
     * in the hotbar meanwhile.
     */
    public void restore(Player player) {
        PlayerInventory inventory = player.getInventory();
        Optional<ItemStack[]> kept = stash.read(player);
        List<ItemStack> strays = new ArrayList<>();
        if (kept.isPresent()) {
            for (int slot = 0; slot < StanceStash.HOTBAR; slot++) {
                ItemStack now = inventory.getItem(slot);
                if (now != null && !now.isEmpty() && !IconTags.isIcon(now)) {
                    strays.add(now);
                }
                ItemStack original = kept.get()[slot];
                inventory.setItem(slot, original == null || original.isEmpty() ? null : original);
            }
            stash.drop(player);
        }
        purge(player);
        for (ItemStack stray : strays) {
            inventory.addItem(stray).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    /** Takes every stance icon out of the inventory, the off hand and the cursor. */
    public void purge(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (IconTags.isIcon(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
        if (IconTags.isIcon(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    /** The real hotbar, for a death that drops it; the stance ends and the stash is spent. */
    public List<ItemStack> takeKeptForDrops(Player player) {
        List<ItemStack> drops = new ArrayList<>();
        inStance.remove(player.getUniqueId());
        stash.read(player).ifPresent(kept -> {
            for (ItemStack item : kept) {
                if (item != null && !item.isEmpty()) {
                    drops.add(item);
                }
            }
        });
        stash.drop(player);
        for (int slot = 0; slot < StanceStash.HOTBAR; slot++) {
            player.getInventory().setItem(slot, null);
        }
        purge(player);
        return drops;
    }

    /** What the hotbar slot in hand casts, if anything. */
    public Optional<Castable> selected(Player player, Hero hero) {
        int slot = player.getInventory().getHeldItemSlot();
        return slot < Hero.LOADOUT_SIZE ? loadouts.at(hero, slot) : Optional.empty();
    }

    public Optional<Metamagic> armed(UUID player) {
        return Optional.ofNullable(armed.get(player));
    }

    /** The next metamagic round the wheel, then none. */
    public Optional<Metamagic> cycle(Player player, Hero hero) {
        Optional<Metamagic> next = Metamagic.after(armed.get(player.getUniqueId()));
        next.ifPresentOrElse(found -> armed.put(player.getUniqueId(), found),
                () -> armed.remove(player.getUniqueId()));
        redraw(player, hero);
        effects.play(player.getUniqueId(), Cues.CLICK);
        return next;
    }

    public void disarm(Player player, Hero hero) {
        if (armed.remove(player.getUniqueId()) != null) {
            redraw(player, hero);
        }
    }

    public void forget(UUID player) {
        inStance.remove(player);
        armed.remove(player);
    }
}
