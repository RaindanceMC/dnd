package de.raindancer.modules.dnd;

import de.raindancer.core.RainsCore;
import de.raindancer.core.data.settings.SettingsStore;
import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.ui.chat.Brand;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.dnd.rules.PerkRule;
import de.raindancer.modules.dnd.rules.ProgressionRule;
import de.raindancer.modules.dnd.service.BodyService;
import de.raindancer.modules.dnd.service.CombatService;
import de.raindancer.modules.dnd.service.HeroService;
import de.raindancer.modules.dnd.service.ItemForge;
import de.raindancer.modules.dnd.service.ItemService;
import de.raindancer.modules.dnd.service.LootService;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

/** Everything this module built, in one place. Owns nothing, only carries. */
public record DndServices(
        Plugin plugin,
        Server server,
        LogChannel log,
        Messages messages,
        Brand brand,
        RainsCore core,

        Supplier<DndSettings> settings,
        SettingsStore<DndSettings> store,

        ProgressionRule progression,
        PerkRule perks,

        HeroService heroes,
        BodyService body,
        CombatService combat,
        ItemService items,
        LootService loot,
        ItemForge itemForge,
        IDndScreensOpener screens) {

    public DndSettings config() {
        return settings.get();
    }

    public Effects effects() {
        return core.effects();
    }
}
