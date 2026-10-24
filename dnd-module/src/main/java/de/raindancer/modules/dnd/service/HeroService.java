package de.raindancer.modules.dnd.service;

import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.actionbar.ActionBarPriority;
import de.raindancer.core.ui.actionbar.ActionBars;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;
import de.raindancer.modules.dnd.rules.LoadoutRule;
import de.raindancer.modules.dnd.rules.ProgressionRule;
import de.raindancer.modules.dnd.rules.SorceryRule;
import de.raindancer.modules.dnd.store.HeroBook;
import net.kyori.adventure.title.Title;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Every online player's hero, kept in memory and written back when it changed.
 *
 * <h2>Writes</h2>
 * Changes happen on the player's thread and only mark the hero dirty; a timer writes the dirty ones off the server's
 * threads, and leaving writes at once. Each write names the revision it was read at. When another server wrote in
 * between — a player who hopped servers faster than the old one could save — the write loses, the newer row is read
 * back, and the few seconds this server added are what is dropped: never the older state overwriting the newer.
 */
public final class HeroService implements IDndService {

    /** One hero as this server holds it. {@code rev} 0 means it is not in the database yet. */
    private record Entry(Hero hero, long rev, boolean dirty) {
    }

    private final Plugin plugin;
    private final Server server;
    private final LogChannel log;
    private final HeroBook book;
    private final ProgressionRule progression;
    private final LoadoutRule loadouts;
    private final SorceryRule sorcery;
    private final de.raindancer.modules.dnd.rules.SubclassRule subclasses;
    private final de.raindancer.modules.dnd.rules.AbilityRule abilities;
    private final Messages messages;
    private final Effects effects;
    private final ActionBars actionBars;

    private final Map<UUID, Entry> heroes = new ConcurrentHashMap<>();
    /** Read, and found to have no hero. */
    private final Set<UUID> without = ConcurrentHashMap.newKeySet();
    /** The database could not be read for them: nothing of theirs is written until it can. */
    private final Set<UUID> unreadable = ConcurrentHashMap.newKeySet();
    private final Object writing = new Object();

    private volatile DndSettings settings = DndSettings.DEFAULTS;
    private volatile Consumer<Player> changed = player -> { };

    public HeroService(Plugin plugin, Server server, LogChannel log, HeroBook book, ProgressionRule progression,
                       LoadoutRule loadouts, SorceryRule sorcery, Messages messages, Effects effects,
                       ActionBars actionBars, de.raindancer.modules.dnd.rules.SubclassRule subclasses) {
        this.subclasses = subclasses;
        this.abilities = new de.raindancer.modules.dnd.rules.AbilityRule(progression);
        this.plugin = plugin;
        this.server = server;
        this.log = log;
        this.book = book;
        this.progression = progression;
        this.loadouts = loadouts;
        this.sorcery = sorcery;
        this.messages = messages;
        this.effects = effects;
        this.actionBars = actionBars;
    }

    @Override
    public void settings(DndSettings updated) {
        this.settings = updated == null ? DndSettings.DEFAULTS : updated;
    }

    /** Told whenever a player's hero changes, on that player's thread: their body and their stance follow. */
    public void onChange(Consumer<Player> listener) {
        this.changed = listener == null ? player -> { } : listener;
    }

    /** The hero as every rule sees it — with the derived scores while ability scores are switched off. */
    public Optional<Hero> of(UUID player) {
        Entry entry = heroes.get(player);
        return entry == null ? Optional.empty() : Optional.of(shown(entry.hero()));
    }

    private Hero shown(Hero stored) {
        return abilities.shown(stored, settings.abilityScores());
    }

    /** Read from the database, so a class may be chosen; false while loading or when it could not be read. */
    public boolean known(UUID player) {
        return heroes.containsKey(player) || without.contains(player);
    }

    public boolean isUnreadable(UUID player) {
        return unreadable.contains(player);
    }

    // ------------------------------------------------------------------ coming and going

    public void joined(Player player) {
        UUID id = player.getUniqueId();
        if (heroes.containsKey(id)) {
            // Still here from a moment ago; whatever is being written for them is this very hero.
            Scheduling.entity(plugin, player, () -> changed.accept(player));
            return;
        }
        Scheduling.async(plugin, () -> {
            HeroBook.Loaded loaded = book.load(id);
            switch (loaded.status()) {
                case FOUND -> {
                    Hero settled = subclasses.settled(loaded.hero());
                    heroes.putIfAbsent(id, new Entry(settled, loaded.rev(), !settled.equals(loaded.hero())));
                    unreadable.remove(id);
                }
                case NONE -> {
                    without.add(id);
                    unreadable.remove(id);
                }
                case UNREADABLE -> {
                    unreadable.add(id);
                    log.warn("The adventurer of {} could not be read; their class is held back until it can be.",
                            player.getName());
                }
            }
            Scheduling.entity(plugin, player, () -> {
                changed.accept(player);
                if (loaded.status() == HeroBook.Loaded.Status.NONE && player.isOnline()) {
                    messages.send(player, "dnd.welcome");
                }
                of(id).filter(subclasses::choosing).ifPresent(hero -> messages.send(player, "dnd.subclass.waiting"));
            });
        });
    }

    /** Writes what changed, then lets go of them unless they are back by then. */
    public void left(UUID player) {
        Scheduling.async(plugin, () -> {
            write(player);
            if (server.getPlayer(player) == null) {
                Entry entry = heroes.get(player);
                if (entry == null || !entry.dirty()) {
                    heroes.remove(player);
                }
                without.remove(player);
                unreadable.remove(player);
            }
        });
    }

    // ------------------------------------------------------------------ changing

    /** Changes the hero on the player's own thread; empty when they have none. */
    public Optional<Hero> change(Player player, UnaryOperator<Hero> how) {
        UUID id = player.getUniqueId();
        Entry[] after = new Entry[1];
        heroes.computeIfPresent(id, (key, entry) -> {
            Hero next = abilities.kept(entry.hero(), subclasses.settled(how.apply(shown(entry.hero()))),
                    settings.abilityScores());
            after[0] = next.equals(entry.hero()) ? entry : new Entry(next, entry.rev(), true);
            return after[0];
        });
        if (after[0] == null) {
            return Optional.empty();
        }
        changed.accept(player);
        return Optional.of(shown(after[0].hero()));
    }

    /** A new class, always from level 1: a level belongs to the class it was earned in. */
    public Optional<Hero> choose(Player player, HeroClass heroClass) {
        UUID id = player.getUniqueId();
        if (unreadable.contains(id) || !known(id)) {
            return Optional.empty();
        }
        Entry old = heroes.get(id);
        Hero fresh = Hero.fresh(id, heroClass, loadouts.starting(heroClass));
        fresh = subclasses.settled(fresh.withSorceryPoints(sorcery.most(fresh)));
        heroes.put(id, new Entry(fresh, old == null ? 0 : old.rev(), true));
        without.remove(id);
        changed.accept(player);
        Scheduling.async(plugin, () -> write(id));
        if (subclasses.choosing(fresh)) {
            messages.send(player, "dnd.subclass.waiting");
        }
        return Optional.of(shown(fresh));
    }

    /** Experience from a kill or a staff member, scaled by the setting. */
    public void grantXp(Player player, long base, String source, boolean scaled) {
        long amount = scaled ? base * Math.max(0, settings.xpPercent()) / 100 : base;
        if (amount <= 0) {
            return;
        }
        Optional<Hero> before = of(player.getUniqueId());
        if (before.isEmpty()) {
            return;
        }
        Optional<Hero> after = change(player, hero -> grown(hero, amount));
        after.ifPresent(now -> {
            actionBars.show(player.getUniqueId(), "dnd-xp",
                    messages.get("dnd.xp-gained", "xp", String.valueOf(amount), "source", source),
                    Duration.ofSeconds(2), ActionBarPriority.LOW);
            if (now.level() > before.get().level()) {
                levelledUp(player, before.get(), now);
            }
        });
    }

    /** Puts a hero at a level, for staff. */
    public Optional<Hero> setLevel(Player player, int level) {
        Optional<Hero> before = of(player.getUniqueId());
        Optional<Hero> after = change(player, hero -> {
            Hero placed = progression.atLevel(hero, level);
            Hero topped = loadouts.pruned(loadouts.toppedUp(placed, hero));
            return topped.withSorceryPoints(Math.min(sorcery.most(topped),
                    hero.sorceryPoints() + Math.max(0, sorcery.most(topped) - sorcery.most(hero))));
        });
        if (before.isPresent() && after.isPresent() && after.get().level() > before.get().level()) {
            levelledUp(player, before.get(), after.get());
        }
        return after;
    }

    private Hero grown(Hero hero, long amount) {
        Hero grown = progression.grown(hero, amount);
        if (grown.level() == hero.level()) {
            return grown;
        }
        int morePoints = sorcery.most(grown) - sorcery.most(hero);
        return loadouts.toppedUp(grown, hero).withSorceryPoints(grown.sorceryPoints() + Math.max(0, morePoints));
    }

    /** A sorcerer's origin, chosen once. Empty when it may not be chosen. */
    public Optional<Hero> chooseSubclass(Player player, de.raindancer.modules.dnd.model.Subclass subclass) {
        Optional<Hero> hero = of(player.getUniqueId());
        if (hero.isEmpty() || !subclasses.mayChoose(hero.get(), subclass)) {
            return Optional.empty();
        }
        return change(player, now -> subclasses.mayChoose(now, subclass) ? now.withSubclass(subclass) : now);
    }

    private void levelledUp(Player player, Hero before, Hero now) {
        effects.play(player.getUniqueId(), Cues.REWARD);
        player.showTitle(Title.title(messages.get("dnd.level-up-title", "level", String.valueOf(now.level())),
                messages.get("dnd.level-up-subtitle", "class", now.heroClass().title())));
        messages.send(player, "dnd.level-up", "level", String.valueOf(now.level()), "class",
                now.heroClass().title());
        if (now.subclass() != null && now.subclass() != before.subclass()) {
            messages.send(player, "dnd.subclass.gained", "subclass", now.subclass().title());
        }
        if (subclasses.choosing(now) && !subclasses.choosing(before)) {
            messages.send(player, "dnd.subclass.waiting");
        }
        if (settings.abilityScores() && now.improvements() > before.improvements()) {
            messages.send(player, "dnd.improvement-earned", "points", String.valueOf(now.improvements()));
        }
        List<String> fresh = now.loadout().stream().filter(id -> !id.isEmpty() && !before.loadout().contains(id))
                .toList();
        for (String id : fresh) {
            LoadoutRule.castable(id).ifPresent(castable -> messages.send(player, "dnd.learned",
                    "name", castable.title()));
        }
    }

    /** Takes the hero out of memory and the database, for staff. */
    public void reset(UUID player, Runnable after) {
        heroes.remove(player);
        without.add(player);
        Scheduling.async(plugin, () -> {
            synchronized (writing) {
                book.delete(player);
            }
            if (after != null) {
                after.run();
            }
        });
    }

    // ------------------------------------------------------------------ writing

    /** Writes every hero that changed. Off the server's threads. */
    public void flush() {
        for (UUID id : List.copyOf(heroes.keySet())) {
            write(id);
        }
    }

    /** One hero, if it changed. One write at a time, so two can never race over the same revision. */
    void write(UUID id) {
        synchronized (writing) {
            Entry entry = heroes.get(id);
            if (entry == null || !entry.dirty() || unreadable.contains(id)) {
                return;
            }
            HeroBook.Saved saved = book.save(entry.hero(), entry.rev());
            switch (saved.status()) {
                case WRITTEN -> heroes.computeIfPresent(id, (key, now) ->
                        new Entry(now.hero(), saved.rev(), now.hero() != entry.hero()));
                case CONFLICT -> {
                    HeroBook.Loaded newer = book.load(id);
                    if (newer.status() == HeroBook.Loaded.Status.FOUND) {
                        heroes.put(id, new Entry(newer.hero(), newer.rev(), false));
                        log.warn("The adventurer of {} was changed on another server meanwhile; the newer one "
                                + "was kept.", id);
                        Player online = server.getPlayer(id);
                        if (online != null) {
                            Scheduling.entity(plugin, online, () -> changed.accept(online));
                        }
                    } else if (newer.status() == HeroBook.Loaded.Status.NONE) {
                        heroes.computeIfPresent(id, (key, now) -> new Entry(now.hero(), 0, true));
                    }
                }
                case FAILED -> log.warn("The adventurer of {} could not be written; trying again shortly.", id);
            }
        }
    }

    public int dirty() {
        return (int) heroes.values().stream().filter(Entry::dirty).count();
    }

    public int held() {
        return heroes.size();
    }
}
