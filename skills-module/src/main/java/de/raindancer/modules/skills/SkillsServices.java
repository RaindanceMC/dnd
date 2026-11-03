package de.raindancer.modules.skills;

import de.raindancer.core.RainsCore;
import de.raindancer.core.data.settings.SettingsStore;
import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.ui.chat.Brand;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.skills.rules.SkillRule;
import de.raindancer.modules.skills.rules.ProgressionRule;
import de.raindancer.modules.skills.service.BodyService;
import de.raindancer.modules.skills.service.CombatService;
import de.raindancer.modules.skills.service.HeroService;
import de.raindancer.modules.skills.service.ItemForge;
import de.raindancer.modules.skills.service.ItemService;
import de.raindancer.modules.skills.service.LootService;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

/** Everything this module built, in one place. Owns nothing, only carries. */
public record SkillsServices(
        Plugin plugin,
        Server server,
        LogChannel log,
        Messages messages,
        Brand brand,
        RainsCore core,

        Supplier<SkillsSettings> settings,
        SettingsStore<SkillsSettings> store,

        ProgressionRule progression,
        SkillRule perks,

        HeroService heroes,
        BodyService body,
        CombatService combat,
        ItemService items,
        LootService loot,
        ItemForge itemForge,
        ISkillsScreensOpener screens) {

    public SkillsSettings config() {
        return settings.get();
    }

    public Effects effects() {
        return core.effects();
    }
}
