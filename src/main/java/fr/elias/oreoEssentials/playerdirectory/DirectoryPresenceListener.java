// File: src/main/java/fr/elias/oreoEssentials/playerdirectory/DirectoryPresenceListener.java
package fr.elias.oreoEssentials.playerdirectory;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class DirectoryPresenceListener implements Listener {
    private final PlayerDirectory dir;
    private final String server;

    public DirectoryPresenceListener(PlayerDirectory dir, String server) {
        this.dir = dir;
        this.server = server;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        try {
            dir.upsertPresence(e.getPlayer().getUniqueId(), e.getPlayer().getName(), server);
        } catch (Throwable t) {
            OreoEssentials.get().getLogger().warning("[PlayerDirectory] upsertPresence failed: " + t.getMessage());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        try {
            dir.clearCurrentServer(e.getPlayer().getUniqueId());
        } catch (Throwable t) {
            OreoEssentials.get().getLogger().warning("[PlayerDirectory] clearCurrentServer failed: " + t.getMessage());
        }
    }

    /** Optional: call once after register to mark already-online players (e.g., /reload). */
    public void backfillOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try { dir.setCurrentServer(p.getUniqueId(), server); } catch (Throwable ignored) {}
        }
    }
}
