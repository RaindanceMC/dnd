package de.raindancer.modules.skills;

import de.raindancer.core.data.settings.SettingsStore;
import de.raindancer.core.data.sql.Database;
import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.api.FlexModule;
import de.raindancer.modules.api.ModuleCommand;
import de.raindancer.modules.api.ModuleContext;
import de.raindancer.modules.api.ModuleInfo;
import de.raindancer.modules.skills.listener.CombatListener;
import de.raindancer.modules.skills.listener.ItemListener;
import de.raindancer.modules.skills.listener.PresenceListener;
import de.raindancer.modules.skills.model.SkillCatalog;
import de.raindancer.modules.skills.rules.FightRule;
import de.raindancer.modules.skills.rules.ItemRule;
import de.raindancer.modules.skills.rules.LootRule;
import de.raindancer.modules.skills.rules.MobRule;
import de.raindancer.modules.skills.rules.SkillRule;
import de.raindancer.modules.skills.rules.ProgressionRule;
import de.raindancer.modules.skills.model.SkillDef;
import de.raindancer.modules.skills.screen.SkillTreeMenu;
import de.raindancer.modules.skills.screen.SkillsMenu;
import de.raindancer.modules.skills.service.SkillViews;
import de.raindancer.modules.skills.service.BodyService;
import de.raindancer.modules.skills.service.CombatService;
import de.raindancer.modules.skills.service.HeroService;
import de.raindancer.modules.skills.service.ISkillsService;
import de.raindancer.modules.skills.service.ItemForge;
import de.raindancer.modules.skills.service.ItemService;
import de.raindancer.modules.skills.service.LootService;
import de.raindancer.modules.skills.service.ScrollService;
import de.raindancer.modules.skills.service.SpellCues;
import de.raindancer.modules.skills.service.TargetService;
import de.raindancer.modules.skills.store.SkillsDatabase;
import de.raindancer.modules.skills.store.SkillsPack;
import de.raindancer.modules.skills.store.HeroBook;
import de.raindancer.modules.skills.util.PermissionNodes;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Skills on a survival server: one adventurer level earned by playing — mining, digging, chopping, farming, crafting,
 * smelting, building, trading, enchanting and more, never ordinary monsters' farms — and a skill point every level, up
 * to a cap, for any skill's tree; each skill a canvas of branches that end in mastery badges. Magic items too.
 *
 * <p>Shipped through the standard wrapper this is {@code RainsSkills} (it was {@code RainsAdventurers}, and before
 * 0.11.0 tabletop classes). The module id, its settings and its database are still called {@code dnd}, so a server's
 * settings and heroes stay where they were.
 */
public final class SkillsModule implements FlexModule {

    private static final ModuleInfo INFO = ModuleInfo.of("dnd", "Skills", "0.14.0")
            .describedAs("Skills with trees of their own, levelled by playing, with mastery badges — and magic items.")
            .by("Raindancer118");

    private static final long SECOND_TICKS = 20L;
    private static final long MEND_SECONDS = 5L;
    private static final long FLUSH_SECONDS = 10L;

    private LogChannel log;
    private Server server;
    private SkillsServices services;

    @Override
    public ModuleInfo info() {
        return INFO;
    }

    @Override
    public void enable(ModuleContext context) {
        log = context.log();
        server = context.plugin().getServer();
        SettingsStore<SkillsSettings> settings = context.settings(SkillsSettings.class, SkillsSettings.DEFAULTS);

        context.core().messages().defineFrom(SkillsModule.class.getResourceAsStream("messages.yml"),
                context.chat().brand()::chatPrefix);
        context.core().messages().seriousFrom(SkillsModule.class.getResourceAsStream("messages-serious.yml"));

        int registered = PermissionNodes.register(server);
        if (registered > 0) {
            log.info("{} permission(s) registered.", registered);
        }

        Database database = context.core().databases().shared(SkillsDatabase.NAME, SkillsDatabase.SCHEMA);
        HeroBook book = new HeroBook(database);
        if (!book.usable()) {
            log.error("The adventurers database could not be opened. Heroes are not read or written until it can be.");
        }

        RandomGenerator random = new SplittableRandom();
        var messages = context.core().messages();
        var effects = context.core().effects();
        var actionBars = context.core().actionBars();

        ProgressionRule progression = new ProgressionRule();
        SkillRule perks = new SkillRule(SkillCatalog.all(), () -> settings.current().pointsCap(),
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

        List<ISkillsService> all = List.of(heroes, body, targets, cues, combat, itemForge, scrolls, items, loot);
        all.forEach(service -> service.settings(settings.current()));
        all.forEach(service -> settings.onChange(service::settings));

        services = new SkillsServices(context.plugin(), server, log, messages, context.chat().brand(), context.core(),
                settings::current, settings, progression, perks, heroes, body, combat, items, loot, itemForge,
                new LiveScreens());

        heroes.onChange(player -> body.refresh(player, heroes.of(player.getUniqueId())));

        context.listener(new PresenceListener(services));
        context.listener(new CombatListener(services));
        context.listener(new ItemListener(services));
        de.raindancer.modules.skills.skill.gathering.GatheringSkills.enable(context, services);
        // Farming, Foraging, Cooking, Alchemy and Taming: each its own package, listener and timers.
        de.raindancer.modules.skills.skill.farming.FarmingListener.start(context, services);
        de.raindancer.modules.skills.skill.foraging.ForagingListener.start(context, services);
        de.raindancer.modules.skills.skill.cooking.CookingListener.start(context, services);
        de.raindancer.modules.skills.skill.alchemy.AlchemyListener.start(context, services);
        de.raindancer.modules.skills.skill.taming.TamingListener.start(context, services);
        enableMach(context, settings, messages, itemForge);
        // Experience for what players do — never for ordinary monsters, so a mob farm pays nothing.
        Object listening = de.raindancer.core.world.activity.Activities.listen(context.plugin(), combat::did);
        context.closeWith(() -> de.raindancer.core.world.activity.Activities.stop(listening));

        SkillsCommands.ready(services);
        SkillsPack.contribute(context.plugin(), context.core());
        context.closeWith(() -> SkillsPack.withdraw(context.core()));

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
                new de.raindancer.modules.skills.service.AdventurerScreens(heroes, progression, book::top,
                        player -> context.core().identities().nameOf(player), System::currentTimeMillis)
                        .register(screens, context.plugin());
                context.closeWith(() -> de.raindancer.modules.skills.service.AdventurerScreens.KINDS
                        .forEach(screens::unregister));
            }
        } catch (LinkageError olderCore) {
            log.info("RainsCore is older than 2.10.0: no adventurer screens until it is updated.");
        }

        SkillViews views = new SkillViews(heroes, perks, progression);
        views.register(context.plugin(), (viewer, parent, skill) -> new SkillTreeMenu(services, viewer, parent, skill)
                .open());
        context.closeWith(views::unregister);
        de.raindancer.modules.skills.skill.CraftSkills.enable(context, services, database);
        // A fully mastered skill's glyph beside the name.
        de.raindancer.core.ui.identity.NameBadges.register("skills-mastery", "Mastered skills", player -> heroes
                .of(player).map(hero -> SkillCatalog.all().stream().filter(skill -> perks.fullyMastered(hero, skill))
                        .map(SkillDef::badge).reduce("", String::concat))
                .filter(glyphs -> !glyphs.isEmpty())
                .map(glyphs -> net.kyori.adventure.text.Component.text(glyphs,
                        net.kyori.adventure.text.format.NamedTextColor.GOLD)));
        context.closeWith(() -> de.raindancer.core.ui.identity.NameBadges.unregister("skills-mastery"));

        log.info("Skills are up: {} skills, {} nodes; heroes are kept in {}.", SkillCatalog.all().size(),
                SkillCatalog.all().stream().mapToInt(skill -> skill.tree().nodes().size()).sum(), database.describe());
    }

    /** Opening this module's screens, without the command knowing the menu classes. */
    private final class LiveScreens implements ISkillsScreensOpener {

        @Override
        public void skills(Player viewer) {
            if (services.heroes().ensure(viewer).isEmpty()) {
                services.messages().send(viewer, "dnd.still-reading");
                return;
            }
            new SkillsMenu(services, viewer, null).open();
        }

        @Override
        public void tree(Player viewer, SkillDef skill) {
            if (services.heroes().ensure(viewer).isEmpty()) {
                services.messages().send(viewer, "dnd.still-reading");
                return;
            }
            new SkillTreeMenu(services, viewer, new SkillsMenu(services, viewer, null), skill).open();
        }
    }

    /**
     * The Potion of Mach Fuck: a dragon head brewed into a plain potion makes it (a mix of the server's brewer,
     * taken back when the module stops), and a timer flies whoever has it in them.
     */
    private void enableMach(ModuleContext context, SettingsStore<SkillsSettings> settings,
                            de.raindancer.core.ui.messages.Messages messages, ItemForge itemForge) {
        AdventurerAdvancements advancements = new AdventurerAdvancements(context.core().advancements());
        advancements.define();
        de.raindancer.modules.skills.rules.MachRule rule = new de.raindancer.modules.skills.rules.MachRule();
        de.raindancer.modules.skills.service.MachService mach = new de.raindancer.modules.skills.service.MachService(
                messages, context.core().actionBars(), context.core().effects(), advancements, rule,
                System::currentTimeMillis);
        mach.settings(settings.current());
        settings.onChange(mach::settings);
        context.listener(new de.raindancer.modules.skills.listener.MachListener(mach, advancements));

        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(SkillsPack.NAMESPACE, "mach_fuck");
        var brewer = server.getPotionBrewer();
        brewer.removePotionMix(key);
        brewer.addPotionMix(new io.papermc.paper.potion.PotionMix(key,
                itemForge.make(de.raindancer.modules.skills.model.MagicItem.POTION_OF_MACH_FUCK),
                io.papermc.paper.potion.PotionMix.createPredicateChoice(bottle -> rule.brewsFrom(bottle.getType(),
                        de.raindancer.modules.skills.store.ItemTags.itemOf(bottle).isPresent())),
                new org.bukkit.inventory.RecipeChoice.MaterialChoice(org.bukkit.Material.DRAGON_HEAD)));
        context.closeWith(() -> brewer.removePotionMix(key));

        de.raindancer.modules.skills.service.MachWorlds worlds = new de.raindancer.modules.skills.service.MachWorlds();
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
        return SkillsCommands.declared();
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
        SkillsCommands.stopped();
    }
}
