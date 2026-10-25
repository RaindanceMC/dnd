package de.raindancer.modules.dnd.rules;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;

/** What the adventurers' screens say, as MiniMessage text. */
public final class ScreenLineRule implements IDndRule {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final String HEAD = "<gold><bold>Your adventurer</bold></gold>";
    private static final int BAR = 10;
    private static final String[] PLACE = {"<gold>", "<gray>", "<#cd7f32>"};

    /** One line of the top. */
    public record Row(String name, String heroClass, int level, long xp) {
    }

    public String bar(double progress) {
        int filled = (int) Math.floor(Math.max(0.0, Math.min(1.0, progress)) * BAR);
        String full = "▰".repeat(filled);
        String empty = "▰".repeat(BAR - filled);
        if (filled == 0) {
            return "<dark_gray>" + empty;
        }
        return filled == BAR ? "<green>" + full : "<green>" + full + "</green><dark_gray>" + empty;
    }

    /**
     * @param path        the class's second path, null before it is chosen
     * @param nextXp      what the next level takes, 0 at the highest level
     * @param progress    0 to 1 through the current level
     * @param hitDiceLeft of {@code hitDice} (each a d{@code hitDie})
     */
    public String sheet(String heroClass, String path, int level, long xp, long nextXp, double progress,
                        int hitDiceLeft, int hitDice, int hitDie) {
        StringBuilder text = new StringBuilder(HEAD).append("\n<white>").append(MINI.escapeTags(heroClass))
                .append("</white>");
        if (path != null && !path.isBlank()) {
            text.append(" <gray>· ").append(MINI.escapeTags(path));
        }
        boolean highest = nextXp <= 0;
        text.append("\n<gray>level <white>").append(level).append(highest ? " <gold>(highest)" : "")
                .append("\n<gray>xp <white>").append(xp);
        if (!highest) {
            text.append("</white><gray>/").append(nextXp).append("\n").append(bar(progress));
        }
        return text.append("\n<gray>hit dice <white>").append(hitDiceLeft).append("</white><gray>/").append(hitDice)
                .append(" (d").append(hitDie).append(")").toString();
    }

    public String none() {
        return HEAD + "\n<gray>No class chosen yet <dark_gray>· <white>/adventurer";
    }

    public String top(List<Row> rows) {
        StringBuilder text = new StringBuilder("<gold><bold>Top adventurers</bold></gold>");
        if (rows.isEmpty()) {
            return text.append("\n<gray>Nobody has set out yet").toString();
        }
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            text.append('\n').append(i < PLACE.length ? PLACE[i] : "<white>").append(i + 1).append(". ")
                    .append(MINI.escapeTags(row.name())).append(" <dark_gray>· <gray>")
                    .append(MINI.escapeTags(row.heroClass())).append(" lv ").append(row.level());
        }
        return text.toString();
    }

    @Override
    public String describe() {
        return "what the adventurers' screens say: a hero's own sheet summary and the top by experience";
    }
}
