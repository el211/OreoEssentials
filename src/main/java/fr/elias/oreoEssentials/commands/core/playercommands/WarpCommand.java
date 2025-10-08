// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/WarpCommand.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.WarpTeleportRequestPacket;
import fr.elias.oreoEssentials.services.WarpDirectory;
import fr.elias.oreoEssentials.services.WarpService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class WarpCommand implements OreoCommand {
    private final WarpService warps;

    public WarpCommand(WarpService warps) { this.warps = warps; }

    @Override public String name() { return "warp"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.warp"; }
    @Override public String usage() { return "<name>|list"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        final OreoEssentials plugin = OreoEssentials.get();
        final var log = plugin.getLogger();
        if (!(sender instanceof Player p)) return true;

        // /warp or /warp list => list warps
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            var list = warps.listWarps();
            p.sendMessage("§eWarps: §7" + (list.isEmpty() ? "(none)" :
                    list.stream().collect(Collectors.joining(", "))));
            log.info("[WarpCmd] " + p.getName() + " requested warp list. Count=" + list.size());
            return true;
        }

        final String raw = args[0];
        final String warpName = raw.trim().toLowerCase(Locale.ROOT);

        // Resolve server owner for this warp
        final String localServer = plugin.getConfigService().serverName();
        final WarpDirectory warpDir = plugin.getWarpDirectory();
        String targetServer = (warpDir != null ? warpDir.getWarpServer(warpName) : localServer);
        if (targetServer == null || targetServer.isBlank()) targetServer = localServer;

        log.info("[WarpCmd] Player=" + p.getName() + " UUID=" + p.getUniqueId()
                + " warp=" + warpName
                + " localServer=" + localServer
                + " targetServer=" + targetServer
                + " warpDir=" + (warpDir == null ? "null" : "ok"));

        // Local warp -> direct teleport
        if (targetServer.equalsIgnoreCase(localServer)) {
            Location l = warps.getWarp(warpName);
            if (l == null) {
                p.sendMessage(ChatColor.RED + "Warp not found.");
                log.warning("[WarpCmd] Local warp not found. warp=" + warpName);
                return true;
            }
            try {
                p.teleport(l);
                p.sendMessage(ChatColor.GREEN + "Teleported to warp " + ChatColor.AQUA + warpName + ChatColor.GREEN + ".");
                log.info("[WarpCmd] Local teleport success. warp=" + warpName + " loc=" + l);
            } catch (Exception ex) {
                log.warning("[WarpCmd] Local teleport exception: " + ex.getMessage());
                p.sendMessage(ChatColor.RED + "Teleport failed: " + ex.getMessage());
            }
            return true;
        }

        // Remote warp: publish to the target server’s queue, then proxy-switch
        final PacketManager pm = plugin.getPacketManager();
        log.info("[WarpCmd] Remote warp. pm=" + (pm == null ? "null" : "ok")
                + " pm.init=" + (pm != null && pm.isInitialized()));

        if (pm != null && pm.isInitialized()) {
            String requestId = UUID.randomUUID().toString();
            plugin.getLogger().info("[WARP/SEND] from=" + localServer
                    + " player=" + p.getUniqueId()
                    + " nameArg='" + warpName + "' -> targetServer=" + targetServer
                    + " requestId=" + requestId);

            WarpTeleportRequestPacket pkt = new WarpTeleportRequestPacket(p.getUniqueId(), warpName, targetServer, requestId);
            PacketChannel ch = PacketChannel.individual(targetServer);
            pm.sendPacket(ch, pkt);
        } else {
            p.sendMessage(ChatColor.RED + "Cross-server messaging is disabled.");
            p.sendMessage(ChatColor.GRAY + "Use " + ChatColor.AQUA + "/server " + targetServer + ChatColor.GRAY +
                    " then run " + ChatColor.AQUA + "/warp " + warpName);
            return true;
        }

        if (sendPlayerToServer(p, targetServer)) {
            p.sendMessage(ChatColor.YELLOW + "Sending you to " + ChatColor.AQUA + targetServer +
                    ChatColor.YELLOW + "… you'll be teleported to warp " + ChatColor.AQUA + warpName +
                    ChatColor.YELLOW + " on arrival.");
            log.info("[WarpCmd] Proxy switch initiated. player=" + p.getUniqueId() + " to=" + targetServer);
        } else {
            p.sendMessage(ChatColor.RED + "Failed to switch you to " + targetServer + ".");
            log.warning("[WarpCmd] Proxy switch failed to " + targetServer + " (check Velocity/Bungee server name match).");
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
            log.info("[WarpCmd] Sent plugin message 'Connect' to proxy. server=" + serverName);
            return true;
        } catch (Exception ex) {
            log.warning("[WarpCmd] Failed to send Connect plugin message: " + ex.getMessage());
            return false;
        }
    }
}
