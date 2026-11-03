package de.raindancer.modules.skills;

import de.raindancer.modules.skills.model.SkillDef;
import org.bukkit.entity.Player;

/** Opening one of this module's screens without naming its class — so a command built at bootstrap can. */
public interface ISkillsScreensOpener {

    /** Every skill, with level, points and mastery. */
    void skills(Player viewer);

    /** One skill's tree. */
    void tree(Player viewer, SkillDef skill);
}
