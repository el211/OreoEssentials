// File: src/main/java/fr/elias/oreoEssentials/homes/HomeTeleportBroker.java
package fr.elias.oreoEssentials.homes;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.HomeTeleportRequestPacket;
import fr.elias.oreoEssentials.services.HomeService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class HomeTeleportBroker implements Listener {
    private final Plugin plugin;
    private final HomeService homes;
    private final PacketManager pm;
    private final String thisServer;
    private final Logger log;

    /** latest requested home per player (guards against stale packets) */
    private final Map<UUID, String> lastRequestedHome = new ConcurrentHashMap<>();
    /** latest requestId per player (to disambiguate multiple close requests) */
    private final Map<UUID, String> lastRequestId = new ConcurrentHashMap<>();
    /** presence means we owe a teleport on join / while online */
    private final Map<UUID, Boolean> pending = new ConcurrentHashMap<>();

    public HomeTeleportBroker(Plugin plugin, HomeService homes, PacketManager pm) {
        this.plugin = plugin;
        this.homes = homes;
        this.pm = pm;
        this.thisServer = OreoEssentials.get().getConfigService().serverName();
        this.log = plugin.getLogger();

        // Loud startup confirmation that the broker is live on this server
        log.info("[HOME/BROKER] up on server=" + thisServer + " pm.init=" + pm.isInitialized());

        // Subscribe HOME teleport requests
        pm.subscribe(HomeTeleportRequestPacket.class, (channel, pkt) -> {
            if (!thisServer.equalsIgnoreCase(pkt.getTargetServer())) {
                log.info("[HOME/REQ] ignoring (not my server). this=" + thisServer
                        + " target=" + pkt.getTargetServer());
                return;
            }

            final UUID id = pkt.getPlayerId();
            final String home = pkt.getHomeName();
            final String reqId = pkt.getRequestId();

            lastRequestedHome.put(id, home);
            lastRequestId.put(id, reqId);
            pending.put(id, Boolean.TRUE);

            final Player online = Bukkit.getPlayer(id);
            // Loud proof this server accepted the matching packet
            log.info("[HOME/REQ] recv server=" + thisServer
                    + " player=" + id
                    + " home=" + home
                    + " requestId=" + reqId
                    + " online=" + (online != null && online.isOnline()));

            if (online != null && online.isOnline()) {
                applyWithRetries(id);
            }
        });

        // Events
        Bukkit.getPluginManager().registerEvents(this, plugin);
        log.info("[BROKER/HOME] listening. server=" + thisServer + " pm.init=" + pm.isInitialized());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        final UUID id = e.getPlayer().getUniqueId();
        if (!pending.containsKey(id)) return;
        log.info("[HOME/JOIN] player=" + id + " intent=" + lastRequestedHome.get(id)
                + " requestId=" + lastRequestId.get(id));
        applyWithRetries(id);
    }

    /* ---------------- helpers ---------------- */

    private void applyWithRetries(UUID id) {
        // try at 0t, +2t, +10t, +20t
        runOnce(id, 0);
        runOnce(id, 2);
        runOnce(id, 10);
        runOnce(id, 20);
    }


    private void runOnce(UUID id, int tick) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            final Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) {
                log.info("[HOME/Retry] tick=" + tick + " player offline: " + id);
                return;
            }

            final String expectHome = lastRequestedHome.get(id);
            final String reqId = lastRequestId.get(id);
            if (expectHome == null || reqId == null) {
                log.info("[HOME/Retry] tick=" + tick + " no-intent for " + id);
                return;
            }

            final Location loc = homes.getHome(id, expectHome);
            if (loc == null) {
                log.warning("[HOME/Retry] tick=" + tick + " home not found here. player=" + id
                        + " home=" + expectHome + " requestId=" + reqId);
                // stop trying for this intent
                pending.remove(id);
                lastRequestedHome.remove(id);
                lastRequestId.remove(id);
                return;
            }

            final boolean ok = p.teleport(loc);
            log.info("[HOME/Retry] tick=" + tick
                    + " teleported=" + ok
                    + " player=" + p.getName()
                    + " -> " + expectHome
                    + " requestId=" + reqId
                    + " loc=" + shortLoc(loc));

            if (tick == 20) {
                // final attempt done; clear flags
                pending.remove(id);
                lastRequestedHome.remove(id);
                lastRequestId.remove(id);
            }
        }, tick);
    }

    private static String shortLoc(Location l) {
        return "loc=" + (l.getWorld() != null ? l.getWorld().getName() : "null")
                + "(" + fmt(l.getX()) + "," + fmt(l.getY()) + "," + fmt(l.getZ()) + ")"
                + " yaw=" + fmt(l.getYaw()) + " pitch=" + fmt(l.getPitch());
    }

    private static String fmt(double d) { return String.format(java.util.Locale.ROOT, "%.2f", d); }
    private static String fmt(float f)  { return String.format(java.util.Locale.ROOT, "%.2f", f); }
}
