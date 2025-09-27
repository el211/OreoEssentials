package fr.elias.oreoEssentials.portals;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class PortalsListener implements Listener {
    private final PortalsManager manager;

    public PortalsListener(PortalsManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player p = e.getPlayer();
        // Only check when actually changed block or Yaw/Pitch moved—MoveEvent fires a lot.
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        manager.tickMove(p, e.getTo());
    }
}
