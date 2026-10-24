package de.raindancer.modules.dnd;

import de.raindancer.core.data.settings.SettingsStore;
import de.raindancer.core.data.sql.Database;
import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.api.FlexModule;
import de.raindancer.modules.api.ModuleCommand;
import de.raindancer.modules.api.ModuleContext;
import de.raindancer.modules.api.ModuleInfo;
import de.raindancer.modules.dnd.listener.CombatListener;
import de.raindancer.modules.dnd.listener.ItemListener;
import de.raindancer.modules.dnd.listener.PresenceListener;
import de.raindancer.modules.dnd.listener.RestListener;
import de.raindancer.modules.dnd.listener.StanceListener;
import de.raindancer.modules.dnd.rules.CheckRule;
import de.raindancer.modules.dnd.rules.ConcentrationRule;
import de.raindancer.modules.dnd.rules.DamageRule;
import de.raindancer.modules.dnd.rules.FeatureRule;
import de.raindancer.modules.dnd.rules.HealthRule;
import de.raindancer.modules.dnd.rules.ItemRule;
import de.raindancer.modules.dnd.rules.LootRule;
import de.raindancer.modules.dnd.rules.SubclassRule;
import de.raindancer.modules.dnd.rules.SurgeRule;
import de.raindancer.modules.dnd.rules.LoadoutRule;
import de.raindancer.modules.dnd.rules.MartialRule;
import de.raindancer.modules.dnd.rules.MetamagicRule;
import de.raindancer.modules.dnd.rules.MobRule;
import de.raindancer.modules.dnd.rules.ProgressionRule;
import de.raindancer.modules.dnd.rules.RestRule;
import de.raindancer.modules.dnd.rules.SorceryRule;
import de.raindancer.modules.dnd.rules.SpellSlotRule;
import de.raindancer.modules.dnd.screen.ClassMenu;
import de.raindancer.modules.dnd.screen.LoadoutMenu;
import de.raindancer.modules.dnd.screen.SheetMenu;
import de.raindancer.modules.dnd.service.BodyService;
import de.raindancer.modules.dnd.service.CastingService;
import de.raindancer.modules.dnd.service.CombatService;
import de.raindancer.modules.dnd.service.ConcentrationService;
import de.raindancer.modules.dnd.service.ConditionService;
import de.raindancer.modules.dnd.service.FeatureService;
import de.raindancer.modules.dnd.service.HeroService;
import de.raindancer.modules.dnd.service.HudService;
import de.raindancer.modules.dnd.service.IDndService;
import de.raindancer.modules.dnd.service.IconForge;
import de.raindancer.modules.dnd.service.ItemForge;
import de.raindancer.modules.dnd.service.ItemService;
import de.raindancer.modules.dnd.service.LootService;
import de.raindancer.modules.dnd.service.SurgeService;
import de.raindancer.modules.dnd.service.RestService;
import de.raindancer.modules.dnd.service.SpellCues;
import de.raindancer.modules.dnd.service.SpellService;
import de.raindancer.modules.dnd.service.StanceService;
import de.raindancer.modules.dnd.service.TargetService;
import de.raindancer.modules.dnd.store.DndDatabase;
import de.raindancer.modules.dnd.store.HeroBook;
import de.raindancer.modules.dnd.store.StanceStash;
import de.raindancer.modules.dnd.util.PermissionNodes;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Tabletop-style adventurers on a survival server.
 *
 * <p>Shipped through the standard wrapper this is {@code RainsAdventurers}. A player chooses one of six classes and
 * levels from 1 to 20 by defeating monsters. Casters spend spell slots, sorcerers sorcery points and metamagic, the
 * fighting classes rage, surge and sneak attack. F takes up the casting stance, where the hotbar is the hero's spells
 * and features; a short rest is standing still a while, a long rest a night's sleep. Rules after the System
 * Reference Document 5.1 (CC BY 4.0).
 */
public final class DndModule implements FlexModule {

    private static final ModuleInfo INFO = ModuleInfo.of("dnd", "Adventurers", "0.2.0")
            .describedAs("Classes, levels, spell slots, sorcery points and spells cast from a casting stance.")
            .by("Raindancer118");

    private static final long HUD_TICKS = 10L;
    private static final long SECOND_TICKS = 20L;
    private static final long FLUSH_SECONDS = 10L;

    private LogChannel log;
    private Server server;
    private DndServices services;

    @Override
    public ModuleInfo info() {
        return INFO;
    }

    @Override
    public void enable(ModuleContext context) {
        log = context.log();
        server = context.plugin().getServer();
        SettingsStore<DndSettings> settings = context.settings(DndSettings.class, DndSettings.DEFAULTS);

        context.core().messages().defineFrom(DndModule.class.getResourceAsStream("messages.yml"),
                context.chat().brand()::chatPrefix);
        context.core().messages().seriousFrom(DndModule.class.getResourceAsStream("messages-serious.yml"));

        int registered = PermissionNodes.register(server);
        if (registered > 0) {
            log.info("{} permission(s) registered.", registered);
        }

        Database database = context.core().databases().shared(DndDatabase.NAME, DndDatabase.SCHEMA);
        HeroBook book = new HeroBook(database);
        if (!book.usable()) {
            log.error("The adventurers database could not be opened. Heroes are not read or written until it can be.");
        }

        RandomGenerator random = new SplittableRandom();
        var messages = context.core().messages();
        var effects = context.core().effects();
        var actionBars = context.core().actionBars();

        ProgressionRule progression = new ProgressionRule();
        SpellSlotRule slots = new SpellSlotRule();
        SorceryRule sorcery = new SorceryRule(slots);
        FeatureRule features = new FeatureRule();
        HealthRule health = new HealthRule();
        CheckRule checks = new CheckRule(progression);
        DamageRule damage = new DamageRule(progression);
        RestRule rests = new RestRule(sorcery, health);
        LoadoutRule loadouts = new LoadoutRule(slots);
        MetamagicRule metamagic = new MetamagicRule();
        MartialRule martial = new MartialRule(features);
        MobRule mobs = new MobRule();
        ConcentrationRule concentrationRule = new ConcentrationRule();
        SubclassRule subclasses = new SubclassRule();
        SurgeRule surgeRule = new SurgeRule();
        LootRule lootRule = new LootRule();
        ItemRule itemRule = new ItemRule();

        HeroService heroes = new HeroService(context.plugin(), server, log, book, progression, loadouts, sorcery,
                messages, effects, actionBars, subclasses);
        BodyService body = new BodyService(health, martial);
        ConditionService conditions = new ConditionService(System::currentTimeMillis);
        IconForge icons = new IconForge(slots, features, damage);
        StanceService stance = new StanceService(messages, effects, new StanceStash(), icons, loadouts, metamagic);
        TargetService targets = new TargetService(context.core().combat());
        SpellCues cues = new SpellCues(effects);
        cues.define();
        ConcentrationService concentration = new ConcentrationService(messages, effects, checks, concentrationRule,
                random, System::currentTimeMillis);
        SpellService spells = new SpellService(context.plugin(), messages, targets, cues, checks, damage, mobs,
                features, heroes, conditions, body, concentration, random);
        FeatureService featureService = new FeatureService(context.plugin(), messages, effects, targets, cues,
                conditions, features, slots, checks, mobs, random);
        SurgeService surges = new SurgeService(context.plugin(), messages, effects, heroes, spells, body, conditions,
                targets, cues, sorcery, surgeRule, random);
        featureService.onTides(surges::owe);
        CastingService casting = new CastingService(messages, effects, actionBars, heroes, stance, spells,
                featureService, concentration, conditions, slots, features, metamagic, loadouts);
        casting.surgesWith(surges);
        surges.onResetWaits(player -> casting.endWaits(player.getUniqueId()));
        ItemForge itemForge = new ItemForge(System::currentTimeMillis);
        ItemService items = new ItemService(messages, effects, heroes, itemForge, itemRule, cues, random,
                System::currentTimeMillis);
        items.castWith(casting::castFromItem);
        spells.saveBonusFromItems(items::saveBonus);
        LootService loot = new LootService(messages, effects, itemForge, lootRule, mobs, random);
        CombatService combat = new CombatService(messages, actionBars, heroes, conditions, concentration, martial,
                features, checks, mobs, random);
        combat.weaponBonus(items::weaponBonus);
        RestService resting = new RestService(messages, effects, actionBars, heroes, body, conditions, rests, health,
                System::currentTimeMillis);
        HudService hud = new HudService(messages, actionBars, stance, slots, sorcery);

        List<IDndService> all = List.of(heroes, body, conditions, icons, stance, targets, cues, concentration, spells,
                featureService, casting, combat, resting, hud, surges, itemForge, items, loot);
        all.forEach(service -> service.settings(settings.current()));
        all.forEach(service -> settings.onChange(service::settings));

        services = new DndServices(context.plugin(), server, log, messages, context.chat().brand(), context.core(),
                settings::current, settings, progression, slots, sorcery, features, health, checks, rests, loadouts,
                metamagic, heroes, body, stance, casting, combat, concentration, conditions, resting, hud, icons,
                featureService, surges, items, loot, itemForge, subclasses, new LiveScreens());

        heroes.onChange(player -> {
            Optional<de.raindancer.modules.dnd.model.Hero> hero = heroes.of(player.getUniqueId());
            body.refresh(player, hero);
            hero.ifPresentOrElse(found -> stance.redraw(player, found), () -> {
                if (stance.isIn(player.getUniqueId())) {
                    stance.leave(player, true);
                }
            });
        });

        context.listener(new PresenceListener(services));
        context.listener(new StanceListener(services));
        context.listener(new CombatListener(services));
        context.listener(new RestListener(services));
        context.listener(new ItemListener(services));

        DndCommands.ready(services);

        var hudTimer = Scheduling.globalTimer(context.plugin(), HUD_TICKS, HUD_TICKS, task -> {
            for (Player player : server.getOnlinePlayers()) {
                if (stance.isIn(player.getUniqueId())) {
                    Scheduling.entity(context.plugin(), player,
                            () -> heroes.of(player.getUniqueId()).ifPresent(hero -> hud.show(player, hero)));
                }
            }
        });
        var secondTimer = Scheduling.globalTimer(context.plugin(), SECOND_TICKS, SECOND_TICKS, task -> {
            for (Player player : server.getOnlinePlayers()) {
                Scheduling.entity(context.plugin(), player, () -> {
                    Optional<de.raindancer.modules.dnd.model.Hero> hero = heroes.of(player.getUniqueId());
                    concentration.tick(player);
                    resting.tick(player);
                    featureService.tick(player, hero);
                    body.refresh(player, hero);
                });
            }
        });
        var flushTimer = Scheduling.asyncTimer(context.plugin(), FLUSH_SECONDS, FLUSH_SECONDS, task -> heroes.flush());
        for (var timer : List.of(Optional.ofNullable(hudTimer), Optional.ofNullable(secondTimer),
                Optional.ofNullable(flushTimer))) {
            timer.ifPresent(scheduled -> context.closeWith(scheduled::cancel));
        }

        // A reload while players are online: they did not join, so they are read now.
        for (Player player : server.getOnlinePlayers()) {
            stance.restore(player);
            heroes.joined(player);
        }

        log.info("Adventurers are up: {} classes, {} spells; heroes are kept in {}.",
                de.raindancer.modules.dnd.model.HeroClass.values().length,
                de.raindancer.modules.dnd.model.Spell.values().length, database.describe());
    }

    /** Opening this module's screens, without the command knowing the menu classes. */
    private final class LiveScreens implements IDndScreensOpener {

        @Override
        public void sheet(Player viewer) {
            if (services.heroes().of(viewer.getUniqueId()).isEmpty()) {
                classes(viewer);
                return;
            }
            new SheetMenu(services, viewer, null).open();
        }

        @Override
        public void classes(Player viewer) {
            new ClassMenu(services, viewer, null).open();
        }

        @Override
        public void spellbook(Player viewer) {
            new LoadoutMenu(services, viewer, null).open();
        }
    }

    @Override
    public List<ModuleCommand> commands() {
        return DndCommands.declared();
    }

    /** Every stance ends and every hotbar goes back before anything is torn down; then every hero is written. */
    @Override
    public void disable() {
        if (services != null && server != null) {
            for (Player player : server.getOnlinePlayers()) {
                services.stance().leave(player, true);
                services.concentration().forget(player);
                services.features().foldWings(player);
                services.body().strip(player);
            }
            services.surges().cleanUp();
            services.heroes().flush();
        }
        DndCommands.stopped();
    }
}
