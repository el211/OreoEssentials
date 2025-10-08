package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.HomeTeleportRequestPacket;
import fr.elias.oreoEssentials.services.HomeService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.*;
import java.util.stream.Collectors;

public class HomeCommand implements OreoCommand, TabCompleter {

    private final HomeService homes;

    public HomeCommand(HomeService homes) {
        this.homes = homes;
    }

    @Override public String name() { return "home"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.home"; }
    @Override public String usage() { return "<name>|list"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        // /home or /home list -> show homes
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            Set<String> set = safeHomes(p.getUniqueId());
            if (set.isEmpty()) {
                p.sendMessage(ChatColor.YELLOW + "You have no homes. Use " + ChatColor.AQUA + "/sethome <name>");
                return true;
            }
            String list = set.stream()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.joining(ChatColor.GRAY + ", " + ChatColor.AQUA));
            p.sendMessage(ChatColor.GOLD + "Homes: " + ChatColor.AQUA + list);
            p.sendMessage(ChatColor.GRAY + "Tip: " + ChatColor.AQUA + "/" + label + " <name>" + ChatColor.GRAY + " to teleport.");
            return true;
        }

        String raw = args[0];
        String key = normalize(raw);

        // Where does the home live?
        String targetServer = homes.homeServer(p.getUniqueId(), key);
        String localServer  = homes.localServer();
        if (targetServer == null) targetServer = localServer;

        // Local teleport
        if (targetServer.equalsIgnoreCase(localServer)) {
            Location loc = homes.getHome(p.getUniqueId(), key);
            if (loc == null) {
                p.sendMessage(ChatColor.RED + "Home not found: " + ChatColor.YELLOW + raw);
                suggestClosest(p, key);
                return true;
            }
            p.teleport(loc);
            p.sendMessage(ChatColor.GREEN + "Teleported to home " + ChatColor.AQUA + key + ChatColor.GREEN + ".");
            return true;
        }

        // Cross-server: publish to the TARGET SERVER'S QUEUE (not global), then proxy switch
        final OreoEssentials plugin = OreoEssentials.get();
        final PacketManager pm = plugin.getPacketManager();

        if (pm != null && pm.isInitialized()) {
            final String requestId = java.util.UUID.randomUUID().toString();
            plugin.getLogger().info("[HOME/SEND] from=" + homes.localServer()
                    + " player=" + p.getUniqueId()
                    + " nameArg='" + key + "' -> targetServer=" + targetServer
                    + " requestId=" + requestId);

            // Build the packet
            HomeTeleportRequestPacket pkt = new HomeTeleportRequestPacket(p.getUniqueId(), key, targetServer, requestId);

            // *** KEY CHANGE: publish to the target server’s individual channel ***
            PacketChannel targetChannel = PacketChannel.individual(targetServer);
            pm.sendPacket(targetChannel, pkt);
        } else {
            p.sendMessage(ChatColor.RED + "Cross-server messaging is disabled. Ask an admin to enable RabbitMQ.");
            p.sendMessage(ChatColor.GRAY + "You can still switch with " + ChatColor.AQUA + "/server " + targetServer + ChatColor.GRAY + " and run " + ChatColor.AQUA + "/home " + key);
            return true;
        }

        // Switch via proxy
        boolean switched = sendPlayerToServer(p, targetServer);
        if (switched) {
            p.sendMessage(ChatColor.YELLOW + "Sending you to " + ChatColor.AQUA + targetServer +
                    ChatColor.YELLOW + "… you'll be teleported to home " + ChatColor.AQUA + key + ChatColor.YELLOW + " on arrival.");
        } else {
            p.sendMessage(ChatColor.RED + "Failed to switch you to " + targetServer + ".");
            p.sendMessage(ChatColor.GRAY + "Make sure '" + targetServer + "' matches the server name in your proxy config.");
        }
        return true;
    }

    /* ---------------- tab completion ---------------- */

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player p)) return List.of();
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return safeHomes(p.getUniqueId()).stream()
                    .filter(n -> n.startsWith(partial))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    /* ---------------- helpers ---------------- */

    private Set<String> safeHomes(UUID id) {
        try {
            Set<String> set = homes.homes(id);
            return set == null ? Collections.emptySet() : set;
        } catch (Throwable t) {
            return Collections.emptySet();
        }
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private void suggestClosest(Player p, String key) {
        List<String> suggestions = safeHomes(p.getUniqueId()).stream()
                .filter(n -> n.contains(key))
                .limit(5)
                .collect(Collectors.toList());
        if (!suggestions.isEmpty()) {
            p.sendMessage(ChatColor.GRAY + "Did you mean: " + ChatColor.AQUA +
                    String.join(ChatColor.GRAY + ", " + ChatColor.AQUA, suggestions));
        }
    }

    /** Proxy switch */
    private boolean sendPlayerToServer(Player p, String serverName) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            p.sendPluginMessage(OreoEssentials.get(), "BungeeCord", b.toByteArray());
            return true;
        } catch (Exception ex) {
            Bukkit.getLogger().warning("[OreoEssentials] Failed to send Connect plugin message: " + ex.getMessage());
            return false;
        }
    }
}
