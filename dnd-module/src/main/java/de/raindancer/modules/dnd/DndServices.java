package de.raindancer.modules.dnd;

import de.raindancer.core.RainsCore;
import de.raindancer.core.data.settings.SettingsStore;
import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.ui.chat.Brand;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.dnd.rules.CheckRule;
import de.raindancer.modules.dnd.rules.FeatureRule;
import de.raindancer.modules.dnd.rules.HealthRule;
import de.raindancer.modules.dnd.rules.LoadoutRule;
import de.raindancer.modules.dnd.rules.MetamagicRule;
import de.raindancer.modules.dnd.rules.ProgressionRule;
import de.raindancer.modules.dnd.rules.RestRule;
import de.raindancer.modules.dnd.rules.SorceryRule;
import de.raindancer.modules.dnd.rules.SpellSlotRule;
import de.raindancer.modules.dnd.service.BodyService;
import de.raindancer.modules.dnd.service.CastingService;
import de.raindancer.modules.dnd.service.CombatService;
import de.raindancer.modules.dnd.service.ConcentrationService;
import de.raindancer.modules.dnd.service.ConditionService;
import de.raindancer.modules.dnd.service.HeroService;
import de.raindancer.modules.dnd.service.HudService;
import de.raindancer.modules.dnd.service.IconForge;
import de.raindancer.modules.dnd.service.RestService;
import de.raindancer.modules.dnd.service.StanceService;
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
        SpellSlotRule slots,
        SorceryRule sorcery,
        FeatureRule features,
        HealthRule health,
        CheckRule checks,
        RestRule rests,
        LoadoutRule loadouts,
        MetamagicRule metamagic,

        HeroService heroes,
        BodyService body,
        StanceService stance,
        CastingService casting,
        CombatService combat,
        ConcentrationService concentration,
        ConditionService conditions,
        RestService resting,
        HudService hud,
        IconForge icons,
        IDndScreensOpener screens) {

    public DndSettings config() {
        return settings.get();
    }

    public Effects effects() {
        return core.effects();
    }
}
