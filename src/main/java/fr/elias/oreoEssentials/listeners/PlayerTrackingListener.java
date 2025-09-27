// File: src/main/java/fr/elias/oreoEssentials/listeners/PlayerTrackingListener.java
package fr.elias.oreoEssentials.listeners;

import fr.elias.oreoEssentials.services.BackService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class PlayerTrackingListener implements Listener {
    private final BackService back;
    public PlayerTrackingListener(BackService back) { this.back = back; }

    @EventHandler public void onTeleport(PlayerTeleportEvent e) {
        if (e.isCancelled()) return;
        back.setLast(e.getPlayer().getUniqueId(), e.getFrom());
    }

    @EventHandler public void onDeath(PlayerDeathEvent e) {
        back.setLast(e.getEntity().getUniqueId(), e.getEntity().getLocation());
    }
}
