// File: src/main/java/fr/elias/oreoEssentials/playerdirectory/DirectoryHeartbeat.java
package fr.elias.oreoEssentials.playerdirectory;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class DirectoryHeartbeat {
    private final PlayerDirectory dir;
    private final String server;
    private BukkitTask task;

    public DirectoryHeartbeat(PlayerDirectory dir, String server) {
        this.dir = dir;
        this.server = server;
    }

    /** Start a lightweight repeating task that "touches" presence. */
    public void start() {
        stop();
        // every 30s on main (safe & cheap: small update per online player)
        this.task = Bukkit.getScheduler().runTaskTimer(
                OreoEssentials.get(),
                () -> {
                    try {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            // ensures name/nameLower + current/lastServer are present
                            dir.upsertPresence(p.getUniqueId(), p.getName(), server);
                        }
                    } catch (Throwable ignored) {}
                },
                20L * 10,   // first run after 10s
                20L * 30    // every 30s
        );
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
