package de.raindancer.modules.dnd.model;

import java.util.random.RandomGenerator;

/** {@code count}d{@code sides} + {@code bonus}. Zero dice is a flat number. */
public record Dice(int count, int sides, int bonus) {

    public static final Dice NONE = new Dice(0, 0, 0);

    public Dice {
        count = Math.max(0, count);
        sides = Math.max(0, sides);
    }

    public static Dice of(int count, int sides) {
        return new Dice(count, sides, 0);
    }

    public static Dice flat(int amount) {
        return new Dice(0, 0, amount);
    }

    public boolean isNone() {
        return count == 0 && bonus == 0;
    }

    /** The same dice, {@code times} as many of them. The bonus stays. */
    public Dice times(int times) {
        return new Dice(count * Math.max(0, times), sides, bonus);
    }

    public Dice plusDice(int more) {
        return new Dice(count + more, sides, bonus);
    }

    public Dice plus(int more) {
        return new Dice(count, sides, bonus + more);
    }

    public int roll(RandomGenerator random) {
        int total = bonus;
        for (int die = 0; die < count; die++) {
            total += sides <= 0 ? 0 : 1 + random.nextInt(sides);
        }
        return total;
    }

    public double average() {
        return bonus + count * (sides + 1) / 2.0;
    }

    public int most() {
        return bonus + count * sides;
    }

    @Override
    public String toString() {
        if (count == 0) {
            return String.valueOf(bonus);
        }
        String dice = count + "d" + sides;
        if (bonus > 0) {
            return dice + "+" + bonus;
        }
        if (bonus < 0) {
            return dice + bonus;
        }
        return dice;
    }
}
