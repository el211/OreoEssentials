// src/main/java/fr/elias/oreoEssentials/homes/TeleportBroker.java
package fr.elias.oreoEssentials.homes;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.HomeTeleportRequestPacket;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.SpawnTeleportRequestPacket;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.WarpTeleportRequestPacket;
import fr.elias.oreoEssentials.services.HomeService;
import fr.elias.oreoEssentials.services.WarpService;
import fr.elias.oreoEssentials.services.SpawnService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportBroker {
    private final OreoEssentials plugin;
    private final String local;

    private final HomeService  homes;
    private final WarpService  warps;
    private final SpawnService spawns;

    // pending teleports if player hasn’t joined yet
    private final Map<UUID, Runnable> pending = new ConcurrentHashMap<>();

    public TeleportBroker(OreoEssentials plugin,
                          HomeService homes,
                          WarpService warps,
                          SpawnService spawns,
                          PacketManager pm) {
        this.plugin = plugin;
        this.homes  = homes;
        this.warps  = warps;
        this.spawns = spawns;
        this.local  = homes != null ? homes.localServer() : Bukkit.getServer().getName();

        // subscribe packets
        pm.subscribe(HomeTeleportRequestPacket.class, (ch, pkt) -> {
            if (!local.equalsIgnoreCase(pkt.getTargetServer())) return;
            queueOrRun(pkt.getPlayerId(), () -> {
                Location loc = homes.getHome(pkt.getPlayerId(), pkt.getHomeName());
                teleport(pkt.getPlayerId(), loc, "home " + pkt.getHomeName());
            });
        });

        pm.subscribe(WarpTeleportRequestPacket.class, (ch, pkt) -> {
            if (!local.equalsIgnoreCase(pkt.getTargetServer())) return;
            queueOrRun(pkt.getPlayerId(), () -> {
                Location loc = warps.getWarp(pkt.getWarpName());
                teleport(pkt.getPlayerId(), loc, "warp " + pkt.getWarpName());
            });
        });

        pm.subscribe(SpawnTeleportRequestPacket.class, (ch, pkt) -> {
            if (!local.equalsIgnoreCase(pkt.getTargetServer())) return;
            queueOrRun(pkt.getPlayerId(), () -> {
                Location loc = spawns.getSpawn();
                teleport(pkt.getPlayerId(), loc, "spawn");
            });
        });
    }

    private void queueOrRun(UUID id, Runnable action) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                pending.put(id, action);
            } else {
                action.run();
            }
        });
    }

    public void onJoin(UUID id) {
        Runnable r = pending.remove(id);
        if (r != null) Bukkit.getScheduler().runTask(plugin, r);
    }

    private void teleport(UUID id, Location loc, String label) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;
        if (loc == null) {
            p.sendMessage("§cTarget " + label + " not found on this server.");
            return;
        }
        p.teleport(loc);
        p.sendMessage("§aTeleported to §b" + label + "§a.");
    }
}
