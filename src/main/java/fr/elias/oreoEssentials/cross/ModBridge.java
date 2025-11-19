// package fr.elias.oreoEssentials.cross;

import com.google.gson.Gson;
import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.cross.CrossModPacket;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class ModBridge {

    private static final Gson GSON = new Gson();

    private final OreoEssentials plugin;
    private final PacketManager packets;
    private final String thisServer;

    public ModBridge(OreoEssentials plugin, PacketManager packets, String thisServer) {
        this.plugin = plugin;
        this.packets = packets;
        this.thisServer = thisServer;

        if (packets == null || !packets.isInitialized()) {
            plugin.getLogger().warning("[MOD-BRIDGE] PacketManager not available; running in local-only mode.");
            return;
        }

        plugin.getLogger().info("[MOD-BRIDGE] Subscribing CrossModPacket on server=" + thisServer);

        packets.subscribe(CrossModPacket.class, (channel, pkt) -> {
            try {
                handleIncoming(pkt);
            } catch (Throwable t) {
                plugin.getLogger().warning("[MOD-BRIDGE] Incoming mod packet error: " + t.getMessage());
            }
        });

        plugin.getLogger().info("[MOD-BRIDGE] Ready on server=" + thisServer);
    }

    // ------------- public API -------------

    public void kill(java.util.UUID targetId, String targetName) {
        if (tryLocalKill(targetId)) return;
        sendRemote(CrossModPacket.Action.KILL, targetId, targetName, null);
    }

    public void kick(java.util.UUID targetId, String targetName, String reason) {
        if (tryLocalKick(targetId, reason)) return;
        sendRemote(CrossModPacket.Action.KICK, targetId, targetName, reason);
    }

    public void ban(java.util.UUID targetId, String targetName, String reason) {
        // For ban, usually you *do* want the DB write even if offline,
        // so you might always run /ban here and then remote kick, etc.
        // Adapt to your ban plugin.
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "ban " + targetName + " " + (reason == null ? "ModGUI" : reason));
        // Also try to kick on the server where they are:
        sendRemote(CrossModPacket.Action.KICK, targetId, targetName, reason);
    }

    // ------------- internals -------------

    private boolean tryLocalKill(java.util.UUID targetId) {
        Player p = Bukkit.getPlayer(targetId);
        if (p == null || !p.isOnline()) return false;
        p.setHealth(0.0);
        return true;
    }

    private boolean tryLocalKick(java.util.UUID targetId, String reason) {
        Player p = Bukkit.getPlayer(targetId);
        if (p == null || !p.isOnline()) return false;
        p.kickPlayer(reason == null ? "Kicked" : reason);
        return true;
    }

    private void sendRemote(CrossModPacket.Action action, java.util.UUID targetId,
                            String targetName, String reason) {
        if (packets == null || !packets.isInitialized()) {
            plugin.getLogger().fine("[MOD-BRIDGE] sendRemote but PacketManager unavailable.");
            return;
        }

        // Ask PlayerDirectory where they are
        var dir = plugin.getPlayerDirectory();
        String currentServer = (dir != null ? dir.getCurrentServer(targetId) : null);
        if (currentServer == null || thisServer.equalsIgnoreCase(currentServer)) {
            // nowhere / here already; nothing more to do
            return;
        }

        CrossModPacket pkt = new CrossModPacket();
        pkt.setAction(action);
        pkt.setTarget(targetId);
        pkt.setTargetName(targetName);
        pkt.setReason(reason);
        pkt.setSourceServer(thisServer);
        pkt.setTargetServer(currentServer);

        packets.sendPacket(PacketChannels.GLOBAL,
                new fr.elias.oreoEssentials.rabbitmq.packet.impl.CrossModPacket(GSON.toJson(pkt)));
    }

    private void handleIncoming(CrossModPacket pkt) {
        // Optional: check targetServer match
        if (pkt.getTargetServer() != null &&
                !thisServer.equalsIgnoreCase(pkt.getTargetServer())) {
            return; // not for us
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(pkt.getTarget());
            if (p == null || !p.isOnline()) return;

            switch (pkt.getAction()) {
                case KILL -> p.setHealth(0.0);
                case KICK -> p.kickPlayer(pkt.getReason() == null ? "Kicked" : pkt.getReason());
                case BAN -> {
                    // Normally ban is done where your ban plugin is installed.
                    // You can optionally kick here:
                    p.kickPlayer(pkt.getReason() == null ? "Banned" : pkt.getReason());
                }
            }
        });
    }
}
