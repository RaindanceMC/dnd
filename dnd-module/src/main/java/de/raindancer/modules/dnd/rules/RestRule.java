package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.Rest;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** What a short and a long rest give back, and how often a long one may be taken. */
public final class RestRule implements IDndRule {

    /** Hit dice spent on a short rest, and the health they bought. */
    public record Spent(int dice, double healed) {
    }

    private final SorceryRule sorcery;
    private final HealthRule health;

    public RestRule(SorceryRule sorcery, HealthRule health) {
        this.sorcery = sorcery;
        this.health = health;
    }

    public boolean mayLongRest(long lastAt, long now, int minutes) {
        return lastAt <= 0 || minutes <= 0 || now - lastAt >= minutes * 60_000L;
    }

    public long waitLeftMillis(long lastAt, long now, int minutes) {
        if (mayLongRest(lastAt, now, minutes)) {
            return 0;
        }
        return lastAt + minutes * 60_000L - now;
    }

    public Hero afterLongRest(Hero hero, long now) {
        return restored(hero, Rest.LONG)
                .withSlotsSpent(List.of())
                .withSorceryPoints(sorcery.most(hero))
                .withHitDiceSpent(Math.max(0, hero.hitDiceSpent() - health.hitDiceBack(hero.level())))
                .withLongRestAt(now);
    }

    public Hero afterShortRest(Hero hero) {
        return restored(hero, Rest.SHORT);
    }

    private static Hero restored(Hero hero, Rest rest) {
        Map<Feature, Integer> kept = new EnumMap<>(Feature.class);
        hero.used().forEach((feature, count) -> {
            if (!rest.restores(feature.recharge())) {
                kept.put(feature, count);
            }
        });
        return hero.withUsed(kept);
    }

    /** Just enough hit dice for what is missing, and no more than are left. */
    public Spent spendHitDice(Hero hero, double missing, int perDie) {
        int left = hero.level() - hero.hitDiceSpent();
        if (missing <= 0 || left <= 0 || perDie <= 0) {
            return new Spent(0, 0.0);
        }
        int dice = Math.min(left, (int) Math.ceil(missing / perDie));
        return new Spent(dice, Math.min(missing, dice * (double) perDie));
    }

    @Override
    public String describe() {
        return "what short and long rests give back, and how often a long rest may be taken";
    }
}
