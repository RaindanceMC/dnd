package de.raindancer.modules.skills.skill.gathering;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.api.ModuleContext;
import de.raindancer.modules.skills.SkillsServices;
import de.raindancer.modules.skills.skill.excavation.ExcavationSkill;
import de.raindancer.modules.skills.skill.mining.MiningSkill;
import de.raindancer.modules.skills.skill.smithing.SmithingSkill;
import de.raindancer.modules.skills.skill.woodcutting.WoodcuttingSkill;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * Mining, Excavation, Woodcutting and Smithing, started together: their listeners, the farm guard they share, and a
 * look every second — and whenever the hand changes — at the tool held, for its speed, and at night vision deep down.
 */
public final class GatheringSkills implements Listener {

    private static final long SECOND = 20L;

    private final SkillsServices services;
    private final SkillValues values;
    private final MiningSkill mining;
    private final ExcavationSkill excavation;
    private final WoodcuttingSkill woodcutting;

    private GatheringSkills(SkillsServices services, SkillValues values, MiningSkill mining,
                            ExcavationSkill excavation, WoodcuttingSkill woodcutting) {
        this.services = services;
        this.values = values;
        this.mining = mining;
        this.excavation = excavation;
        this.woodcutting = woodcutting;
    }

    public static void enable(ModuleContext context, SkillsServices services) {
        SkillValues values = new SkillValues(services);
        NaturalBreaks natural = new NaturalBreaks();
        MiningSkill mining = new MiningSkill(services, values, natural);
        ExcavationSkill excavation = new ExcavationSkill(services, values, natural);
        WoodcuttingSkill woodcutting = new WoodcuttingSkill(services, values, natural);
        GatheringSkills skills = new GatheringSkills(services, values, mining, excavation, woodcutting);
        context.listener(natural);
        context.listener(mining);
        context.listener(excavation);
        context.listener(woodcutting);
        context.listener(new SmithingSkill(services, values));
        context.listener(skills);
        var timer = Scheduling.globalTimer(services.plugin(), SECOND, SECOND, task -> {
            for (Player player : services.server().getOnlinePlayers()) {
                Scheduling.entity(services.plugin(), player, () -> skills.look(player));
            }
        });
        if (timer != null) {
            context.closeWith(timer::cancel);
        }
    }

    private void look(Player player) {
        var tree = values.of(player);
        mining.tick(player, tree);
        excavation.tick(player, tree);
        woodcutting.tick(player, tree);
    }

    private void lookSoon(Player player) {
        Scheduling.entityLater(services.plugin(), player, 1, () -> look(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        lookSoon(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        lookSoon(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        lookSoon(event.getPlayer());
    }
}
