package de.raindancer.modules.skills.skill;

import de.raindancer.core.data.sql.Database;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.actionbar.ActionBarPriority;
import de.raindancer.core.world.activity.Activities;
import de.raindancer.core.world.activity.ActivityKind;
import de.raindancer.core.world.selection.MarkingListener;
import de.raindancer.core.world.selection.MarkingSessions;
import de.raindancer.core.world.selection.MarkingTool;
import de.raindancer.modules.api.ModuleContext;
import de.raindancer.modules.skills.SkillsServices;
import de.raindancer.modules.skills.listener.ISkillsListener;
import de.raindancer.modules.skills.rules.WorthRule;
import de.raindancer.modules.skills.skill.artisan.ArtisanListener;
import de.raindancer.modules.skills.skill.artisan.ArtisanRule;
import de.raindancer.modules.skills.skill.building.BlueprintBook;
import de.raindancer.modules.skills.skill.building.BlueprintCommand;
import de.raindancer.modules.skills.skill.building.BlueprintRule;
import de.raindancer.modules.skills.skill.building.Blueprints;
import de.raindancer.modules.skills.skill.building.BuildingEffect;
import de.raindancer.modules.skills.skill.building.BuildingListener;
import de.raindancer.modules.skills.skill.building.BuildingRule;
import de.raindancer.modules.skills.skill.building.PasteRule;
import de.raindancer.modules.skills.skill.enchanting.EnchantingListener;
import de.raindancer.modules.skills.skill.enchanting.EnchantingRule;
import de.raindancer.modules.skills.skill.exploration.ExplorationEffect;
import de.raindancer.modules.skills.skill.exploration.ExplorationListener;
import de.raindancer.modules.skills.skill.exploration.ExplorationRule;
import de.raindancer.modules.skills.skill.trading.TradingListener;
import de.raindancer.modules.skills.skill.trading.TradingRule;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;

/**
 * Artisan, Building, Trading, Enchanting and Exploration: their listeners, the blueprints behind
 * {@code /skills blueprint}, the attributes they lend (looked at every second, like the fighting skills' body) and
 * Compass Reading's line. Their wording is {@code messages-craft.yml}.
 */
public final class CraftSkills {

    private static final long SECOND_TICKS = 20L;
    private static final String COMPASS_BAR = "skills-compass";

    private CraftSkills() {
    }

    public static void enable(ModuleContext context, SkillsServices services, Database database) {
        var plugin = context.plugin();
        var messages = services.messages();
        messages.defineFrom(CraftSkills.class.getResourceAsStream("/de/raindancer/modules/skills/messages-craft.yml"),
                context.chat().brand()::chatPrefix);
        messages.seriousFrom(CraftSkills.class.getResourceAsStream(
                "/de/raindancer/modules/skills/messages-craft-serious.yml"));
        SkillValues values = new SkillValues(services.heroes()::of, services.perks(), services.settings());
        RandomGenerator random = new SplittableRandom();
        LongSupplier clock = System::currentTimeMillis;

        MarkingTool marker = new MarkingTool(plugin);
        MarkingSessions marking = new MarkingSessions();
        var core = context.core();
        Blueprints blueprints = new Blueprints(plugin, values, new BlueprintBook(database), new BlueprintRule(),
                new PasteRule(), messages, core::land, marker, marking,
                player -> core.punishmentGuard() == null || core.punishmentGuard().mayBuild(player), random, clock);
        context.listener(new MarkingListener(marker, marking, blueprints));
        BlueprintCommand.ready(blueprints);
        context.closeWith(BlueprintCommand::stopped);

        SkillAttributes[] attributes = new SkillAttributes[1];
        Consumer<Player> refresh = player -> attributes[0].refresh(player);
        ExplorationListener exploration = new ExplorationListener(plugin, values, new ExplorationRule(), messages,
                core.structures(), refresh, random, clock);
        List<SkillAttributes.Lent> lent = new ArrayList<>();
        lent.addAll(SkillAttributes.of(List.of(BuildingEffect.BLOCK_REACH, ExplorationEffect.SWIM_SPEED), values));
        lent.add(new SkillAttributes.Lent("tree_sprint_speed", "movement_speed",
                AttributeModifier.Operation.ADD_SCALAR,
                player -> player.isSprinting() ? values.value(player, ExplorationEffect.SPRINT_SPEED) / 100.0 : 0));
        lent.add(new SkillAttributes.Lent("tree_road_runner", "movement_speed",
                AttributeModifier.Operation.ADD_SCALAR, exploration::road));
        attributes[0] = new SkillAttributes(lent);

        List<ISkillsListener> listeners = List.of(
                new ArtisanListener(plugin, values, new ArtisanRule(), messages::raw, random, clock),
                new BuildingListener(plugin, values, new BuildingRule(), random, clock),
                new TradingListener(plugin, values, new TradingRule(), random),
                new EnchantingListener(plugin, values, new EnchantingRule(), messages, random, clock),
                exploration);
        listeners.forEach(context::listener);
        context.listener(new Leaving(listeners, values, blueprints));
        context.closeWith(exploration::stripMounts);

        // A biome seen for the first time is worth more to an explorer, on top of what it earns everybody.
        WorthRule worths = new WorthRule();
        Object listening = Activities.listen(plugin, (player, activity) -> {
            if (activity.kind() != ActivityKind.EXPLORE) {
                return;
            }
            double more = values.value(player, ExplorationEffect.EXPLORE_XP);
            long extra = Math.round(worths.worth(activity) * more / 100);
            if (extra > 0) {
                services.heroes().grantXp(player, extra, "exploring", true);
            }
        });
        context.closeWith(() -> Activities.stop(listening));

        var timer = Scheduling.globalTimer(plugin, SECOND_TICKS, SECOND_TICKS, task -> {
            for (Player player : services.server().getOnlinePlayers()) {
                Scheduling.entity(plugin, player, () -> {
                    attributes[0].refresh(player);
                    exploration.reading(player).ifPresent(reading -> core.actionBars().show(player.getUniqueId(),
                            COMPASS_BAR, messages.get("dnd.exploration.reading", (Object[]) reading),
                            Duration.ofMillis(1_500), ActionBarPriority.LOW));
                });
            }
        });
        if (timer != null) {
            context.closeWith(timer::cancel);
        }
    }

    /** What these skills keep about a player, let go when they leave. */
    private record Leaving(List<ISkillsListener> listeners, SkillValues values, Blueprints blueprints)
            implements org.bukkit.event.Listener {

        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuit(PlayerQuitEvent event) {
            UUID player = event.getPlayer().getUniqueId();
            listeners.forEach(listener -> listener.forget(player));
            values.forget(player);
            blueprints.forget(player);
        }
    }
}
