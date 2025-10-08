// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/SpawnCommand.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.SpawnTeleportRequestPacket;
import fr.elias.oreoEssentials.services.SpawnDirectory;
import fr.elias.oreoEssentials.services.SpawnService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.UUID;

public class SpawnCommand implements OreoCommand {
    private final SpawnService spawn;

    public SpawnCommand(SpawnService spawn) { this.spawn = spawn; }

    @Override public String name() { return "spawn"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.spawn"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        final OreoEssentials plugin = OreoEssentials.get();
        final var log = plugin.getLogger();
        if (!(sender instanceof Player p)) return true;

        final String localServer = plugin.getConfigService().serverName();
        final SpawnDirectory spawnDir = plugin.getSpawnDirectory();
        String targetServer = (spawnDir != null ? spawnDir.getSpawnServer() : localServer);
        if (targetServer == null || targetServer.isBlank()) targetServer = localServer;

        log.info("[SpawnCmd] Player=" + p.getName() + " UUID=" + p.getUniqueId()
                + " localServer=" + localServer
                + " targetServer=" + targetServer
                + " spawnDir=" + (spawnDir == null ? "null" : "ok"));

        // Local spawn -> direct teleport
        if (targetServer.equalsIgnoreCase(localServer)) {
            Location l = spawn.getSpawn();
            if (l == null) {
                p.sendMessage(ChatColor.RED + "Spawn is not set.");
                log.warning("[SpawnCmd] Local spawn not set.");
                return true;
            }
            try {
                p.teleport(l);
                p.sendMessage(ChatColor.GREEN + "Teleported to spawn.");
                log.info("[SpawnCmd] Local teleport success. loc=" + l);
            } catch (Exception ex) {
                log.warning("[SpawnCmd] Local teleport exception: " + ex.getMessage());
                p.sendMessage(ChatColor.RED + "Teleport failed: " + ex.getMessage());
            }
            return true;
        }

        // Remote spawn: publish to the target server’s queue, then proxy-switch
        final PacketManager pm = plugin.getPacketManager();
        log.info("[SpawnCmd] Remote spawn. pm=" + (pm == null ? "null" : "ok")
                + " pm.init=" + (pm != null && pm.isInitialized()));

        if (pm != null && pm.isInitialized()) {
            String requestId = UUID.randomUUID().toString();
            plugin.getLogger().info("[SPAWN/SEND] from=" + localServer
                    + " player=" + p.getUniqueId()
                    + " -> targetServer=" + targetServer
                    + " requestId=" + requestId);

            // Build + publish to the target server’s individual channel
            SpawnTeleportRequestPacket pkt = new SpawnTeleportRequestPacket(p.getUniqueId(), targetServer, requestId);
            PacketChannel ch = PacketChannel.individual(targetServer);
            pm.sendPacket(ch, pkt);
        } else {
            p.sendMessage(ChatColor.RED + "Cross-server messaging is disabled.");
            p.sendMessage(ChatColor.GRAY + "Use " + ChatColor.AQUA + "/server " + targetServer + ChatColor.GRAY + " then run " + ChatColor.AQUA + "/spawn");
            return true;
        }

        if (sendPlayerToServer(p, targetServer)) {
            p.sendMessage(ChatColor.YELLOW + "Sending you to " + ChatColor.AQUA + targetServer +
                    ChatColor.YELLOW + "… you'll be teleported to spawn on arrival.");
            log.info("[SpawnCmd] Proxy switch initiated. player=" + p.getUniqueId() + " to=" + targetServer);
        } else {
            p.sendMessage(ChatColor.RED + "Failed to switch you to " + targetServer + ".");
            log.warning("[SpawnCmd] Proxy switch failed to " + targetServer + " (check Velocity/Bungee server name match).");
        }
        return true;
    }

    /** Bungee/Velocity plugin message switch */
    private boolean sendPlayerToServer(Player p, String serverName) {
        final var plugin = OreoEssentials.get();
        final var log = plugin.getLogger();
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            p.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
            log.info("[SpawnCmd] Sent plugin message 'Connect' to proxy. server=" + serverName);
            return true;
        } catch (Exception ex) {
            log.warning("[SpawnCmd] Failed to send Connect plugin message: " + ex.getMessage());
            return false;
        }
    }
}
