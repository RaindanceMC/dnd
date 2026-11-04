package de.raindancer.modules.skills.skill.gathering;

import org.bukkit.Material;

/** Something a skill hands out: so many of one item. */
public record Loot(Material material, int amount) {
}
