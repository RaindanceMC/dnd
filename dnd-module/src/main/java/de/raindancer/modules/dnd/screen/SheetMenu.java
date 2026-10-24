package de.raindancer.modules.dnd.screen;

import de.raindancer.core.ui.menu.Backdrop;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.modules.dnd.DndServices;
import de.raindancer.modules.dnd.model.Ability;
import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.rules.FeatureRule;
import de.raindancer.modules.dnd.rules.ProgressionRule;
import de.raindancer.modules.dnd.service.IconForge;
import de.raindancer.modules.dnd.store.DndPack;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The character sheet: level, abilities, what is left until the next rest, and the doors to everything else. */
public final class SheetMenu extends Menu implements IDndScreen {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final int BAR = 20;

    private final DndServices services;

    public SheetMenu(DndServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
    }

    /** Where every button sits, matching the frames painted on the sheet's background (art/gui/sheet.py). */
    static final int LEVEL = 2;
    static final int PORTRAIT = 4;
    static final int HIT_DICE = 6;
    static final int[] ABILITIES = {10, 11, 12, 14, 15, 16};
    static final int[] LEDGER = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    static final int SPELLBOOK = 37;
    static final int STANCE = 39;
    static final int REST = 41;
    static final int ORIGIN = 43;

    @Override
    protected Component title() {
        return MINI.deserialize("<dark_gray>Character sheet");
    }

    @Override
    protected Backdrop backdrop() {
        return DndPack.SHEET_BACKDROP;
    }

    /** On the painted sheet the title is the hero's class and level, written on the page. */
    @Override
    public Component windowTitle() {
        if (!designed()) {
            return super.windowTitle();
        }
        return Component.text(services.heroes().of(viewer.getUniqueId())
                .map(hero -> hero.heroClass().title() + ", level " + hero.level())
                .orElse("Character sheet"));
    }

    @Override
    public String breadcrumb() {
        return "Character sheet";
    }

    @Override
    protected void render() {
        Optional<Hero> found = services.heroes().of(viewer.getUniqueId());
        if (found.isEmpty()) {
            set(PORTRAIT, Icons.of(Material.BOOK, "<gray>No class yet",
                    "<yellow>Click<gray> to choose one"), click -> new ClassMenu(services, viewer, this).open());
            return;
        }
        Hero hero = found.get();
        header(hero);
        abilities(hero);
        resources(hero);
        doors(hero);
        danger(Icons.of(Material.BARRIER, "<red>Change class",
                "<gray>You start again at level 1.",
                "<yellow>Click<gray> to see the classes"), click -> new ClassMenu(services, viewer, this).open());
    }

    private void header(Hero hero) {
        ProgressionRule progression = services.progression();
        long next = progression.xpFor(hero.level() + 1);
        int filled = (int) Math.round(progression.progress(hero.xp()) * BAR);
        String bar = "<green>" + "|".repeat(filled) + "<dark_gray>" + "|".repeat(BAR - filled);
        List<String> progress = new ArrayList<>();
        progress.add(hero.subclass() == null ? "<dark_gray>No subclass yet"
                : "<gold>" + hero.subclass().title());
        if (hero.level() >= 20) {
            progress.add("<gray>The highest level there is.");
        } else {
            progress.add("<gray>Experience: <white>" + hero.xp() + "</white> of " + next);
            progress.add(bar);
            progress.add("<dark_gray>Defeat monsters to gain more.");
        }
        set(LEVEL, Icons.of(Material.EXPERIENCE_BOTTLE,
                "<green>Level " + hero.level() + " " + hero.heroClass().title(), progress));

        List<String> lore = new ArrayList<>();
        lore.add("<gray>Proficiency bonus: <white>+" + progression.proficiency(hero.level()));
        if (hero.heroClass().castsSpells()) {
            lore.add("<gray>Spell save DC: <white>" + services.checks().spellDc(hero));
            lore.add("<gray>Spell attack: <white>+" + services.checks().spellAttackBonus(hero));
        }
        var armour = viewer.getAttribute(org.bukkit.attribute.Attribute.ARMOR);
        lore.add("<gray>Armour class: <white>" + services.checks().playerArmourClass(
                armour == null ? 0 : (int) Math.round(armour.getValue()), hero.modifier(Ability.DEXTERITY)));
        set(PORTRAIT, Icons.head(viewer, "<white>" + viewer.getName(), lore));

        int left = hero.level() - hero.hitDiceSpent();
        set(HIT_DICE, Icons.of(Material.RED_DYE, "<red>Hit dice",
                "<gray>Left: <white>" + left + "</white> of " + hero.level() + " (d" + hero.heroClass().hitDie() + ")",
                "<dark_gray>Spent on a short rest to heal;",
                "<dark_gray>half come back on a long rest."));
    }

    private void abilities(Hero hero) {
        int index = 0;
        for (Ability ability : Ability.values()) {
            int score = hero.scores().score(ability);
            int modifier = hero.modifier(ability);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Modifier: <white>" + (modifier >= 0 ? "+" : "") + modifier);
            int save = services.checks().saveBonus(hero, ability);
            lore.add("<gray>Saving throw: <white>" + (save >= 0 ? "+" : "") + save
                    + (hero.heroClass().proficientIn(ability) ? "</white> (proficient)" : ""));
            boolean raise = hero.improvements() > 0 && hero.scores().mayRaise(ability);
            if (hero.improvements() > 0) {
                lore.add("");
                lore.add(raise ? "<yellow>Click<gray> to raise it by one (" + hero.improvements() + " left)"
                        : "<dark_gray>Already at 20.");
            }
            Material icon = switch (ability) {
                case STRENGTH -> Material.IRON_SWORD;
                case DEXTERITY -> Material.BOW;
                case CONSTITUTION -> Material.APPLE;
                case INTELLIGENCE -> Material.BOOK;
                case WISDOM -> Material.COMPASS;
                case CHARISMA -> Material.NAME_TAG;
            };
            set(ABILITIES[index++], Icons.of(icon, "<white>" + ability.title() + " " + score, lore),
                    click -> raise(ability));
        }
    }

    private void raise(Ability ability) {
        Optional<Hero> after = services.heroes().change(viewer, hero -> hero.improvements() > 0
                && hero.scores().mayRaise(ability)
                ? hero.withScores(hero.scores().raised(ability), hero.improvements() - 1) : hero);
        if (after.isPresent() && after.get().improvements() >= 0) {
            services.effects().play(viewer.getUniqueId(), Cues.CLICK);
        }
        refresh();
    }

    private void resources(Hero hero) {
        int column = 0;
        if (hero.heroClass().castsSpells()) {
            List<String> lore = new ArrayList<>();
            for (int level = 1; level <= 9; level++) {
                int most = services.slots().most(hero, level);
                if (most > 0) {
                    lore.add("<gray>" + IconForge.ordinal(level) + ": <white>" + services.slots().available(hero, level)
                            + "</white> of " + most);
                }
            }
            lore.add("<dark_gray>A long rest brings them all back.");
            set(LEDGER[column++], Icons.of(Material.AMETHYST_SHARD, "<light_purple>Spell slots", lore));
        }
        if (services.sorcery().most(hero) > 0) {
            set(LEDGER[column++], Icons.of(Material.AMETHYST_CLUSTER, "<light_purple>Sorcery points",
                    "<gray>Left: <white>" + hero.sorceryPoints() + "</white> of " + services.sorcery().most(hero),
                    "<dark_gray>Spent on metamagic and on making slots.",
                    "<dark_gray>A long rest refills them."));
        }
        FeatureRule features = services.featureRule();
        for (Feature feature : Feature.of(hero)) {
            List<String> lore = new ArrayList<>();
            if (features.isCounted(feature) && features.uses(feature, hero.level()) > 0 && feature.pool() == feature) {
                lore.add(features.isUnlimited(feature, hero.level()) ? "<gray>Uses: <white>unlimited"
                        : "<gray>Uses left: <white>" + features.remaining(hero, feature) + "</white> of "
                        + features.uses(feature, hero.level()));
            }
            for (String line : wrapped(feature.summary())) {
                lore.add("<dark_gray>" + line);
            }
            if (column < LEDGER.length) {
                set(LEDGER[column++], DndPack.dress(Icons.of(feature.icon(), "<gold>" + feature.title(), lore),
                        DndPack.iconOf(feature)));
            }
        }
    }

    private void doors(Hero hero) {
        set(SPELLBOOK, Icons.of(Material.WRITABLE_BOOK, "<aqua>Spellbook",
                "<gray>Choose what sits on your casting stance.", "<yellow>Click<gray> to open"),
                click -> new LoadoutMenu(services, viewer, this).open());
        set(STANCE, Icons.of(Material.BLAZE_ROD, "<aqua>Casting stance",
                "<gray>Your hotbar becomes your spells and features;",
                "<gray>click one to cast it. <white>F</white> enters and leaves.",
                "<yellow>Click<gray> to take it up now"), click -> {
                    viewer.closeInventory();
                    services.stance().enter(viewer, hero);
                });
        set(REST, Icons.of(Material.RED_BED, "<aqua>Short rest",
                "<gray>Stand still for " + services.config().shortRestSeconds() + " seconds to spend hit dice",
                "<gray>and win back what a short rest restores.",
                "<gray>Sleep through a night for a long rest.", "<yellow>Click<gray> to rest"), click -> {
                    viewer.closeInventory();
                    services.resting().startShort(viewer);
                });
        if (services.subclasses().choosing(hero)) {
            set(ORIGIN, Icons.of(Material.CHORUS_FLOWER, "<light_purple>Choose your origin",
                    "<gray>Wild Magic or the Draconic Bloodline.", "<yellow>Click<gray> to choose"),
                    click -> new SubclassMenu(services, viewer, this).open());
            return;
        }
        ItemStack font = Icons.of(Material.AMETHYST_CLUSTER, "<aqua>Font of Magic",
                "<gray>Turn sorcery points into spell slots and back.", "<yellow>Click<gray> to open");
        if (Feature.FONT_OF_MAGIC.has(hero)) {
            set(ORIGIN, font, click -> new FontOfMagicMenu(services, viewer, this).open());
        } else {
            set(ORIGIN, Icons.locked(font, "Sorcerers from level 2"));
        }
    }

    private static List<String> wrapped(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + word.length() > 34) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    @Override
    protected List<String> helpLines() {
        return List.of("Your adventurer at a glance.",
                "",
                "Top: your level, your numbers and your hit dice.",
                "Second row: your six abilities. Ability points from",
                "levels 4, 8, 12, 16 and 19 are spent here.",
                "Third row: spell slots, sorcery points and features.",
                "Bottom: spellbook, stance, rest and Font of Magic.");
    }

    @Override
    public String describe() {
        return "the character sheet: level, abilities, resources and the ways into everything else";
    }
}
