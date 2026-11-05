package de.raindancer.modules.skills.skill;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.api.ModuleContext;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/** Something done for every player online, every so often, each on their own thread. Stops with the module. */
public final class Pulse {

    private Pulse() {
    }

    public static void everyPlayer(ModuleContext context, long periodTicks, Consumer<Player> each) {
        var timer = Scheduling.globalTimer(context.plugin(), periodTicks, periodTicks, task -> {
            for (Player player : context.plugin().getServer().getOnlinePlayers()) {
                Scheduling.entity(context.plugin(), player, () -> each.accept(player));
            }
        });
        if (timer != null) {
            context.closeWith(timer::cancel);
        }
    }
}
