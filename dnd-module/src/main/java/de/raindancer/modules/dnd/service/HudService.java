package de.raindancer.modules.dnd.service;

import de.raindancer.core.ui.actionbar.ActionBarPriority;
import de.raindancer.core.ui.actionbar.ActionBars;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.model.Castable;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.rules.SorceryRule;
import de.raindancer.modules.dnd.rules.SpellSlotRule;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The line above the hotbar while in the stance: what the slot in hand does, and what is left. */
public final class HudService implements IDndService {

    private static final String[] NUMERALS = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};

    private final Messages messages;
    private final ActionBars actionBars;
    private final StanceService stance;
    private final SpellSlotRule slots;
    private final SorceryRule sorcery;

    public HudService(Messages messages, ActionBars actionBars, StanceService stance, SpellSlotRule slots,
                      SorceryRule sorcery) {
        this.messages = messages;
        this.actionBars = actionBars;
        this.stance = stance;
        this.slots = slots;
        this.sorcery = sorcery;
    }

    @Override
    public void settings(DndSettings settings) {
        // The line shows what is, not what is configured.
    }

    public void show(Player player, Hero hero) {
        if (!stance.isIn(player.getUniqueId())) {
            return;
        }
        Optional<Castable> selected = stance.selected(player, hero);
        String name = selected.map(Castable::title).orElse("—");
        String key = sorcery.most(hero) > 0 ? "dnd.hud.sorcerer" : hero.heroClass().castsSpells() ? "dnd.hud.caster"
                : "dnd.hud.martial";
        actionBars.show(player.getUniqueId(), "dnd-stance", messages.get(key, "name", name,
                        "slots", slotLine(hero), "points", String.valueOf(hero.sorceryPoints()),
                        "most", String.valueOf(sorcery.most(hero))),
                Duration.ofMillis(1_200), ActionBarPriority.LOW);
    }

    /** {@code I 2/4  II 3/3}: what is left of each slot level there is. Plain text. */
    String slotLine(Hero hero) {
        List<String> parts = new ArrayList<>();
        for (int level = 1; level <= 9; level++) {
            int most = slots.most(hero, level);
            if (most <= 0) {
                continue;
            }
            parts.add(NUMERALS[level] + " " + slots.available(hero, level) + "/" + most);
        }
        return String.join("  ", parts);
    }
}
