package de.raindancer.modules.skills.screen;

import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.PaginatedMenu;
import de.raindancer.core.ui.tree.SkillView;
import de.raindancer.core.ui.tree.Skills;
import de.raindancer.modules.skills.SkillsServices;
import de.raindancer.modules.skills.model.Hero;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Every skill there is — this module's and any other plugin's, fishing's say — with the player's level, points and
 * mastery in each; a click opens that skill's tree.
 */
public final class SkillsMenu extends PaginatedMenu<SkillView> implements ISkillsScreen {

    private static final int BAR = 10;

    private final SkillsServices services;

    public SkillsMenu(SkillsServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
    }

    @Override
    protected Component title() {
        return Component.text("Your skills", NamedTextColor.DARK_GRAY);
    }

    @Override
    public String breadcrumb() {
        return "Your skills";
    }

    @Override
    protected List<SkillView> entries() {
        return Skills.all();
    }

    @Override
    protected ItemStack icon(SkillView skill) {
        UUID me = viewer.getUniqueId();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Level <white>" + skill.level(me));
        lore.add(bar(skill.progress(me)));
        int points = skill.pointsLeft(me);
        if (points > 0) {
            lore.add("<green>" + points + " point" + (points == 1 ? "" : "s") + " to spend");
        }
        if (skill.branches() > 0) {
            lore.add((skill.fullyMastered(me) ? "<gold>Mastered" : "<gray>Mastery <white>" + skill.mastered(me)
                    + "<gray> of " + skill.branches()));
        }
        lore.add("");
        lore.add("<yellow>Click to open its tree.");
        ItemStack icon = Icons.of(skill.icon(), (skill.fullyMastered(me) ? "<gold>" : "<white>") + skill.title(),
                lore);
        if (skill.fullyMastered(me)) {
            icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return icon;
    }

    private static String bar(double progress) {
        int filled = (int) Math.floor(Math.max(0, Math.min(1, progress)) * BAR);
        return "<green>" + "▰".repeat(filled) + "<dark_gray>" + "▰".repeat(BAR - filled);
    }

    @Override
    protected void onClick(SkillView skill, InventoryClickEvent event) {
        skill.open(viewer, this);
    }

    @Override
    protected List<String> helpLines() {
        Hero hero = services.heroes().of(viewer.getUniqueId()).orElseGet(() -> Hero.fresh(viewer.getUniqueId()));
        return List.of(
                "Every skill and its tree. Adventurer experience comes",
                "from playing: mining, digging, chopping, farming,",
                "fishing, crafting, smelting, cooking, brewing,",
                "building, trading, enchanting, taming, exploring,",
                "travelling, bosses and raids. Each level is a skill",
                "point for any tree, up to " + services.perks().cap() + " — you have "
                        + services.perks().points(hero) + ".");
    }

    @Override
    public String describe() {
        return "every skill, with the player's level, points and mastery in each";
    }
}
