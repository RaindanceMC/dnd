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
import de.raindancer.modules.dnd.model.AdventurerTree;
import de.raindancer.modules.dnd.rules.FightRule;
import de.raindancer.modules.dnd.rules.ItemRule;
import de.raindancer.modules.dnd.rules.LootRule;
import de.raindancer.modules.dnd.rules.MobRule;
import de.raindancer.modules.dnd.rules.PerkRule;
import de.raindancer.modules.dnd.rules.ProgressionRule;
import de.raindancer.modules.dnd.screen.SkillTreeMenu;
import de.raindancer.modules.dnd.service.BodyService;
import de.raindancer.modules.dnd.service.CombatService;
import de.raindancer.modules.dnd.service.HeroService;
import de.raindancer.modules.dnd.service.IDndService;
import de.raindancer.modules.dnd.service.ItemForge;
import de.raindancer.modules.dnd.service.ItemService;
import de.raindancer.modules.dnd.service.LootService;
import de.raindancer.modules.dnd.service.ScrollService;
import de.raindancer.modules.dnd.service.SpellCues;
import de.raindancer.modules.dnd.service.TargetService;
import de.raindancer.modules.dnd.store.DndDatabase;
import de.raindancer.modules.dnd.store.DndPack;
import de.raindancer.modules.dnd.store.HeroBook;
import de.raindancer.modules.dnd.util.PermissionNodes;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Adventurers on a survival server: levels from playing — mining, farming, fishing, trading, enchanting, advancements,
 * raids, bosses and the minutes themselves, never ordinary monsters — a skill point every level for a skill tree of
 * five branches — hearts, blows, arrows, quick feet and magic, with feats like Dual Wielder — and magic items that
 * monsters drop.
 *
 * <p>Shipped through the standard wrapper this is {@code RainsAdventurers}. Before 0.11.0 it was tabletop classes and
 * spells; a hero from then keeps their level and experience and finds every point free to spend.
 */
public final class DndModule implements FlexModule {

    private static final ModuleInfo INFO = ModuleInfo.of("dnd", "Adventurers", "0.12.0")
            .describedAs("Levels from playing, a skill tree to spend them in, and magic items.")
            .by("Raindancer118");

    private static final long SECOND_TICKS = 20L;
    private static final long MEND_SECONDS = 5L;
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
        PerkRule perks = new PerkRule(AdventurerTree.shipped(),
                () -> Duration.ofHours(Math.max(0, settings.current().unlearnHours())));
        MobRule mobs = new MobRule();
        ItemRule itemRule = new ItemRule();

        HeroService heroes = new HeroService(context.plugin(), server, log, book, progression, perks, messages,
                effects, actionBars);
        BodyService body = new BodyService(perks);
        TargetService targets = new TargetService(context.core().combat());
        SpellCues cues = new SpellCues(effects);
        cues.define();
        CombatService combat = new CombatService(context.plugin(), messages, actionBars, effects, heroes, perks,
                new FightRule(), mobs, System::currentTimeMillis);
        ItemForge itemForge = new ItemForge(System::currentTimeMillis);
        ScrollService scrolls = new ScrollService(context.plugin(), targets, cues, itemRule);
        ItemService items = new ItemService(messages, effects, combat, itemForge, itemRule, scrolls, cues, random,
                System::currentTimeMillis);
        LootService loot = new LootService(messages, effects, itemForge, new LootRule(), mobs, random);

        List<IDndService> all = List.of(heroes, body, targets, cues, combat, itemForge, scrolls, items, loot);
        all.forEach(service -> service.settings(settings.current()));
        all.forEach(service -> settings.onChange(service::settings));

        services = new DndServices(context.plugin(), server, log, messages, context.chat().brand(), context.core(),
                settings::current, settings, progression, perks, heroes, body, combat, items, loot, itemForge,
                new LiveScreens());

        heroes.onChange(player -> body.refresh(player, heroes.of(player.getUniqueId())));

        context.listener(new PresenceListener(services));
        context.listener(new CombatListener(services));
        context.listener(new ItemListener(services));
        enableMach(context, settings, messages, itemForge);
        // Experience for what players do — never for ordinary monsters, so a mob farm pays nothing.
        Object listening = de.raindancer.core.world.activity.Activities.listen(context.plugin(), combat::did);
        context.closeWith(() -> de.raindancer.core.world.activity.Activities.stop(listening));

        DndCommands.ready(services);
        DndPack.contribute(context.plugin(), context.core());
        context.closeWith(() -> DndPack.withdraw(context.core()));

        long[] seconds = {0};
        var secondTimer = Scheduling.globalTimer(context.plugin(), SECOND_TICKS, SECOND_TICKS, task -> {
            boolean mend = ++seconds[0] % MEND_SECONDS == 0;
            for (Player player : server.getOnlinePlayers()) {
                Scheduling.entity(context.plugin(), player, () -> {
                    // An off hand changes without an event (a click in the inventory): the body is looked at often.
                    body.refresh(player, heroes.of(player.getUniqueId()));
                    if (mend) {
                        combat.mend(player);
                    }
                });
            }
        });
        var flushTimer = Scheduling.asyncTimer(context.plugin(), FLUSH_SECONDS, FLUSH_SECONDS, task -> heroes.flush());
        for (var timer : List.of(Optional.ofNullable(secondTimer), Optional.ofNullable(flushTimer))) {
            timer.ifPresent(scheduled -> context.closeWith(scheduled::cancel));
        }

        // A reload while players are online: they did not join, so they are read now.
        for (Player player : server.getOnlinePlayers()) {
            heroes.joined(player);
        }

        // Screens for /screen — Core 2.10.0; on an older Core there are simply none.
        try {
            var screens = context.core().screens();
            if (screens != null) {
                new de.raindancer.modules.dnd.service.AdventurerScreens(heroes, progression, book::top,
                        player -> context.core().identities().nameOf(player), System::currentTimeMillis)
                        .register(screens, context.plugin());
                context.closeWith(() -> de.raindancer.modules.dnd.service.AdventurerScreens.KINDS
                        .forEach(screens::unregister));
            }
        } catch (LinkageError olderCore) {
            log.info("RainsCore is older than 2.10.0: no adventurer screens until it is updated.");
        }

        log.info("Adventurers are up: a skill tree of {} nodes; heroes are kept in {}.",
                AdventurerTree.shipped().nodes().size(), database.describe());
    }

    /** Opening this module's screens, without the command knowing the menu classes. */
    private final class LiveScreens implements IDndScreensOpener {

        @Override
        public void tree(Player viewer) {
            if (services.heroes().ensure(viewer).isEmpty()) {
                services.messages().send(viewer, "dnd.still-reading");
                return;
            }
            new SkillTreeMenu(services, viewer, null).open();
        }
    }

    /**
     * The Potion of Mach Fuck: a dragon head brewed into a plain potion makes it (a mix of the server's brewer,
     * taken back when the module stops), and a timer flies whoever has it in them.
     */
    private void enableMach(ModuleContext context, SettingsStore<DndSettings> settings,
                            de.raindancer.core.ui.messages.Messages messages, ItemForge itemForge) {
        AdventurerAdvancements advancements = new AdventurerAdvancements(context.core().advancements());
        advancements.define();
        de.raindancer.modules.dnd.rules.MachRule rule = new de.raindancer.modules.dnd.rules.MachRule();
        de.raindancer.modules.dnd.service.MachService mach = new de.raindancer.modules.dnd.service.MachService(
                messages, context.core().actionBars(), context.core().effects(), advancements, rule,
                System::currentTimeMillis);
        mach.settings(settings.current());
        settings.onChange(mach::settings);
        context.listener(new de.raindancer.modules.dnd.listener.MachListener(mach, advancements));

        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(DndPack.NAMESPACE, "mach_fuck");
        var brewer = server.getPotionBrewer();
        brewer.removePotionMix(key);
        brewer.addPotionMix(new io.papermc.paper.potion.PotionMix(key,
                itemForge.make(de.raindancer.modules.dnd.model.MagicItem.POTION_OF_MACH_FUCK),
                io.papermc.paper.potion.PotionMix.createPredicateChoice(bottle -> rule.brewsFrom(bottle.getType(),
                        de.raindancer.modules.dnd.store.ItemTags.itemOf(bottle).isPresent())),
                new org.bukkit.inventory.RecipeChoice.MaterialChoice(org.bukkit.Material.DRAGON_HEAD)));
        context.closeWith(() -> brewer.removePotionMix(key));

        de.raindancer.modules.dnd.service.MachWorlds worlds = new de.raindancer.modules.dnd.service.MachWorlds();
        Scheduling.global(context.plugin(), () -> worlds.restoreAll(server.getWorlds()));
        context.closeWith(() -> worlds.restoreAll(server.getWorlds()));
        var timer = Scheduling.globalTimer(context.plugin(), 1, 1, task -> {
            java.util.Set<org.bukkit.World> flying = new java.util.HashSet<>();
            for (java.util.UUID id : mach.working()) {
                Player player = server.getPlayer(id);
                if (player != null) {
                    flying.add(player.getWorld());
                }
            }
            worlds.flyingIn(flying, server.getWorlds());
            for (java.util.UUID id : mach.active()) {
                Player player = server.getPlayer(id);
                if (player == null) {
                    mach.forget(id);
                } else {
                    Scheduling.entity(context.plugin(), player, () -> mach.tick(player));
                }
            }
        });
        if (timer != null) {
            context.closeWith(timer::cancel);
        }
    }

    @Override
    public List<ModuleCommand> commands() {
        return DndCommands.declared();
    }

    /** Every modifier goes before anything is torn down; then every hero is written. */
    @Override
    public void disable() {
        if (services != null && server != null) {
            for (Player player : server.getOnlinePlayers()) {
                services.body().strip(player);
            }
            services.heroes().flush();
        }
        DndCommands.stopped();
    }
}
