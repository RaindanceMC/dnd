package de.raindancer.modules.skills.service;

import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.tree.SkillView;
import de.raindancer.core.ui.tree.Skills;
import de.raindancer.modules.skills.SkillsSettings;
import de.raindancer.modules.skills.model.Hero;
import de.raindancer.modules.skills.model.SkillDef;
import de.raindancer.modules.skills.rules.ProgressionRule;
import de.raindancer.modules.skills.rules.SkillRule;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

/**
 * This module's skills in Core's {@link Skills}, so a menu listing every plugin's skills shows them. They share one
 * adventurer level and one purse of points; each has its own tree and mastery.
 */
public final class SkillViews implements ISkillsService {

    private final HeroService heroes;
    private final SkillRule rule;
    private final ProgressionRule progression;

    public SkillViews(HeroService heroes, SkillRule rule, ProgressionRule progression) {
        this.heroes = heroes;
        this.rule = rule;
        this.progression = progression;
    }

    @Override
    public void settings(SkillsSettings settings) {
        // What is shown is the hero's own.
    }

    /** @param open how a skill's tree opens for a viewer, with the menu to go back to */
    public void register(Plugin owner, TreeOpener open) {
        for (SkillDef skill : rule.skills()) {
            Skills.register(owner, new View(skill, open));
        }
    }

    public void unregister() {
        rule.skills().forEach(skill -> Skills.unregister(skill.id()));
    }

    /** Opening a skill's tree, without this service knowing the menu. */
    @FunctionalInterface
    public interface TreeOpener {
        void open(Player viewer, Menu parent, SkillDef skill);
    }

    private Hero hero(UUID player) {
        Optional<Hero> held = heroes.of(player);
        return held.orElseGet(() -> Hero.fresh(player));
    }

    private final class View implements SkillView {

        private final SkillDef skill;
        private final TreeOpener open;

        View(SkillDef skill, TreeOpener open) {
            this.skill = skill;
            this.open = open;
        }

        @Override
        public String id() {
            return skill.id();
        }

        @Override
        public String title() {
            return skill.title();
        }

        @Override
        public Material icon() {
            return skill.icon();
        }

        @Override
        public int order() {
            return skill.order();
        }

        @Override
        public int level(UUID player) {
            return hero(player).level();
        }

        @Override
        public double progress(UUID player) {
            return progression.progress(hero(player).xp());
        }

        @Override
        public int pointsLeft(UUID player) {
            return rule.left(hero(player));
        }

        @Override
        public int branches() {
            return rule.masteryBranches(skill).size();
        }

        @Override
        public int mastered(UUID player) {
            return rule.mastered(hero(player), skill);
        }

        @Override
        public void open(Player viewer, Menu parent) {
            open.open(viewer, parent, skill);
        }
    }
}
