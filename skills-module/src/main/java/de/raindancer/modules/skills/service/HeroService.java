package de.raindancer.modules.skills.service;

import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.actionbar.ActionBarPriority;
import de.raindancer.core.ui.actionbar.ActionBars;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.skills.SkillsSettings;
import de.raindancer.modules.skills.model.Hero;
import de.raindancer.modules.skills.rules.SkillRule;
import de.raindancer.modules.skills.rules.ProgressionRule;
import de.raindancer.modules.skills.store.HeroBook;
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
 * Every online player's adventurer, held in memory and written behind. A hero is read when its player joins and
 * made the first time it is needed — the first monster defeated, the first look at the tree.
 */
public final class HeroService implements ISkillsService {

    /** One hero as this server holds it. {@code rev} 0 means it is not in the database yet. */
    private record Entry(Hero hero, long rev, boolean dirty) {
    }

    private final Plugin plugin;
    private final Server server;
    private final LogChannel log;
    private final HeroBook book;
    private final ProgressionRule progression;
    private final SkillRule perks;
    private final Messages messages;
    private final Effects effects;
    private final ActionBars actionBars;

    private final Map<UUID, Entry> heroes = new ConcurrentHashMap<>();
    /** Read, and found to have no hero. */
    private final Set<UUID> without = ConcurrentHashMap.newKeySet();
    /** The database could not be read for them: nothing of theirs is written until it can. */
    private final Set<UUID> unreadable = ConcurrentHashMap.newKeySet();
    private final Object writing = new Object();

    private volatile SkillsSettings settings = SkillsSettings.DEFAULTS;
    private volatile Consumer<Player> changed = player -> { };

    public HeroService(Plugin plugin, Server server, LogChannel log, HeroBook book, ProgressionRule progression,
                       SkillRule perks, Messages messages, Effects effects, ActionBars actionBars) {
        this.plugin = plugin;
        this.server = server;
        this.log = log;
        this.book = book;
        this.progression = progression;
        this.perks = perks;
        this.messages = messages;
        this.effects = effects;
        this.actionBars = actionBars;
    }

    @Override
    public void settings(SkillsSettings updated) {
        this.settings = updated == null ? SkillsSettings.DEFAULTS : updated;
    }

    /** Told whenever a player's hero changes, on that player's thread: their body follows. */
    public void onChange(Consumer<Player> listener) {
        this.changed = listener == null ? player -> { } : listener;
    }

    public Optional<Hero> of(UUID player) {
        Entry entry = heroes.get(player);
        return entry == null ? Optional.empty() : Optional.of(entry.hero());
    }

    public SkillRule perks() {
        return perks;
    }

    /** Read from the database, so a hero may be made; false while loading or when it could not be read. */
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
                    heroes.putIfAbsent(id, new Entry(loaded.hero(), loaded.rev(), false));
                    unreadable.remove(id);
                }
                case NONE -> {
                    without.add(id);
                    unreadable.remove(id);
                }
                case UNREADABLE -> {
                    unreadable.add(id);
                    log.warn("The adventurer of {} could not be read; their hero is held back until it can be.",
                            player.getName());
                }
            }
            Scheduling.entity(plugin, player, () -> {
                changed.accept(player);
                of(id).filter(hero -> perks.left(hero) > 0).ifPresent(hero -> messages.send(player,
                        "dnd.tree.points-waiting", "points", String.valueOf(perks.left(hero))));
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
            Hero next = how.apply(entry.hero());
            after[0] = next.equals(entry.hero()) ? entry : new Entry(next, entry.rev(), true);
            return after[0];
        });
        if (after[0] == null) {
            return Optional.empty();
        }
        changed.accept(player);
        return Optional.of(after[0].hero());
    }

    /**
     * The player's hero, made at level 1 if they have none yet. Empty while it is still being read or could not be.
     */
    public Optional<Hero> ensure(Player player) {
        UUID id = player.getUniqueId();
        Optional<Hero> known = of(id);
        if (known.isPresent() || unreadable.contains(id) || !without.contains(id)) {
            return known;
        }
        Hero fresh = Hero.fresh(id);
        Entry before = heroes.putIfAbsent(id, new Entry(fresh, 0, true));
        without.remove(id);
        if (before != null) {
            return Optional.of(before.hero());
        }
        changed.accept(player);
        messages.send(player, "dnd.welcome");
        return Optional.of(fresh);
    }

    /** Takes the next rank of a skill's node; the refusal when it may not be taken. */
    public Optional<SkillRule.Refusal> learn(Player player, de.raindancer.modules.skills.model.SkillDef skill,
                                             String node) {
        Optional<Hero> hero = ensure(player);
        if (hero.isEmpty()) {
            return Optional.of(SkillRule.Refusal.UNKNOWN);
        }
        java.util.concurrent.atomic.AtomicReference<Optional<SkillRule.Refusal>> refused =
                new java.util.concurrent.atomic.AtomicReference<>(Optional.empty());
        change(player, now -> {
            refused.set(perks.refusal(now, skill, node));
            return refused.get().isPresent() ? now : now.withRank(skill.key(node), now.rank(skill.key(node)) + 1);
        });
        return refused.get();
    }

    /** Unlearns one skill's tree, its points back; false when it may not be done now. */
    public boolean unlearn(Player player, de.raindancer.modules.skills.model.SkillDef skill, long now) {
        boolean[] done = {false};
        change(player, hero -> {
            done[0] = perks.mayUnlearn(hero, skill, now);
            return done[0] ? hero.unlearnt(skill.id(), now) : hero;
        });
        return done[0];
    }

    /** Experience from a kill or a staff member, scaled by the setting. */
    public void grantXp(Player player, long base, String source, boolean scaled) {
        long amount = scaled ? base * Math.max(0, settings.xpPercent()) / 100 : base;
        if (amount <= 0) {
            return;
        }
        Optional<Hero> before = ensure(player);
        if (before.isEmpty()) {
            return;
        }
        Optional<Hero> after = change(player, hero -> progression.grown(hero, amount));
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
        Optional<Hero> before = ensure(player);
        Optional<Hero> after = change(player, hero -> progression.atLevel(hero, level));
        if (before.isPresent() && after.isPresent() && after.get().level() > before.get().level()) {
            levelledUp(player, before.get(), after.get());
        }
        return after;
    }

    private void levelledUp(Player player, Hero before, Hero now) {
        effects.play(player.getUniqueId(), Cues.REWARD);
        player.showTitle(Title.title(messages.get("dnd.level-up-title", "level", String.valueOf(now.level())),
                messages.get("dnd.level-up-subtitle", "points", String.valueOf(perks.left(now)))));
        messages.send(player, "dnd.level-up", "level", String.valueOf(now.level()),
                "points", String.valueOf(perks.left(now)));
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
