package de.raindancer.modules.dnd.util;

import org.bukkit.Server;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.util.List;

/**
 * What this module asks about somebody. Registered in code, so a module hosted in another plugin still has them; an
 * unregistered node would answer "operators only" and refuse every ordinary player.
 */
public final class PermissionNodes {

    /** Choosing a class, casting, resting: being an adventurer at all. */
    public static final String PLAY = "rainsadventurers.play";

    /** Setting levels, granting experience, resting or resetting somebody else. */
    public static final String ADMIN = "rainsadventurers.admin";

    private PermissionNodes() {
    }

    public static List<Permission> declared() {
        return List.of(
                new Permission(PLAY, "Choose a class, cast spells and rest", PermissionDefault.TRUE),
                new Permission(ADMIN, "Set levels, grant experience and reset adventurers", PermissionDefault.OP));
    }

    /** @return how many were added */
    public static int register(Server server) {
        if (server == null) {
            return 0;
        }
        int added = 0;
        for (Permission permission : declared()) {
            if (server.getPluginManager().getPermission(permission.getName()) != null) {
                continue;
            }
            try {
                server.getPluginManager().addPermission(permission);
                added++;
            } catch (IllegalArgumentException alreadyThere) {
                // Registered by another copy between the check and the add.
            }
        }
        return added;
    }
}
