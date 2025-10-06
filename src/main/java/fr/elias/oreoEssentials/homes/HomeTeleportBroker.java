// File: src/main/java/fr/elias/oreoEssentials/homes/HomeTeleportBroker.java
package fr.elias.oreoEssentials.homes;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.HomeTeleportRequestPacket;
import fr.elias.oreoEssentials.services.HomeService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HomeTeleportBroker implements Listener {
    private final OreoEssentials plugin;
    private final HomeService homes;
    private final String local;
    private final Map<UUID, String> pending = new ConcurrentHashMap<>();

    public HomeTeleportBroker(OreoEssentials plugin, HomeService homes, fr.elias.oreoEssentials.rabbitmq.packet.PacketManager pm) {
        this.plugin = plugin;
        this.homes = homes;
        this.local = homes.localServer();

        // Subscribe for cross-server home handoffs
        pm.subscribe(HomeTeleportRequestPacket.class, (channel, pkt) -> {
            if (!local.equalsIgnoreCase(pkt.getTargetServer())) return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(pkt.getPlayerId());
                if (p != null && p.isOnline()) {
                    teleportNow(p.getUniqueId(), pkt.getHomeName());
                } else {
                    // player not here yet, store and handle on join
                    pending.put(pkt.getPlayerId(), pkt.getHomeName());
                }
            });
        });

        // listen for joins to complete pending teleports
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        String home = pending.remove(id);
        if (home != null) teleportNow(id, home);
    }

    private void teleportNow(UUID id, String home) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;
        var loc = homes.getHome(id, home);
        if (loc == null) {
            p.sendMessage("§cHome not found on this server anymore.");
            return;
        }
        p.teleport(loc);
        p.sendMessage("§aTeleported to home §b" + home + "§a.");
    }
}
