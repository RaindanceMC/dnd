package de.raindancer.modules.dnd.service;

import de.raindancer.core.ui.hologram.ScreenKind;
import de.raindancer.core.ui.hologram.Screens;
import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.rules.ProgressionRule;
import de.raindancer.modules.dnd.rules.ScreenLineRule;
import de.raindancer.modules.dnd.store.HeroBook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;

/**
 * The adventurers' screens for Core's {@code /screen}: each player's own sheet summary, and the top by experience —
 * read from the database at most once a minute, since a screen asks every two seconds.
 */
public final class AdventurerScreens implements IDndService {

    public static final List<String> KINDS = List.of("adventurer-you", "adventurer-top");
    static final long TOP_TTL_MILLIS = 60_000;
    static final int TOP_SHOWN = 8;
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final HeroService heroes;
    private final ProgressionRule progression;
    private final IntFunction<List<HeroBook.Standing>> top;
    private final Function<UUID, Optional<String>> names;
    private final LongSupplier clock;
    private final ScreenLineRule rule = new ScreenLineRule();
    private volatile String topText;
    private volatile long topAt;

    public AdventurerScreens(HeroService heroes, ProgressionRule progression, IntFunction<List<HeroBook.Standing>> top,
                             Function<UUID, Optional<String>> names, LongSupplier clock) {
        this.heroes = heroes;
        this.progression = progression;
        this.top = top;
        this.names = names;
        this.clock = clock;
    }

    @Override
    public void settings(DndSettings settings) {
        // Nothing configurable.
    }

    public void register(Screens screens, Plugin owner) {
        screens.register(owner, ScreenKind.personal("adventurer-you", "Your adventurer",
                "Everybody's own class, level, experience to the next level and hit dice.", Material.IRON_SWORD,
                this::you));
        screens.register(owner, ScreenKind.everyone("adventurer-top", "Top adventurers",
                "The most experienced adventurers of the network, with class and level.", Material.GOLDEN_HELMET,
                () -> MINI.deserialize(topText())));
    }

    private Component you(ScreenKind.Viewer viewer) {
        Optional<Hero> hero = heroes.of(viewer.id());
        if (hero.isEmpty()) {
            return MINI.deserialize(rule.none());
        }
        Hero held = hero.get();
        long next = held.level() >= Hero.MOST_LEVEL ? 0 : progression.xpFor(held.level() + 1);
        return MINI.deserialize(rule.sheet(held.heroClass().title(),
                held.subclass() == null ? null : held.subclass().title(), held.level(), held.xp(), next,
                progression.progress(held.xp()), held.level() - held.hitDiceSpent(), held.level(),
                held.heroClass().hitDie()));
    }

    String topText() {
        long now = clock.getAsLong();
        String held = topText;
        if (held != null && now - topAt < TOP_TTL_MILLIS) {
            return held;
        }
        List<ScreenLineRule.Row> rows = top.apply(TOP_SHOWN).stream()
                .map(standing -> new ScreenLineRule.Row(names.apply(standing.player()).orElse("somebody"),
                        standing.heroClass().title(), standing.level(), standing.xp()))
                .toList();
        topText = rule.top(rows);
        topAt = now;
        return topText;
    }
}
