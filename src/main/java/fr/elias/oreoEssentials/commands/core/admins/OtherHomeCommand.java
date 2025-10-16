package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.OtherHomeTeleportRequestPacket;
import fr.elias.oreoEssentials.services.HomeService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class OtherHomeCommand implements OreoCommand, org.bukkit.command.TabCompleter {

    private final OreoEssentials plugin;
    private final HomeService homes;

    public OtherHomeCommand(OreoEssentials plugin, HomeService homes) {
        this.plugin = plugin;
        this.homes = homes;
    }

    @Override public String name() { return "otherhome"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.homes.other"; }
    @Override public String usage() { return "<player> <home>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player admin)) return true;
        if (!sender.hasPermission("oreo.homes.other")) {
            sender.sendMessage(ChatColor.RED + "You don’t have permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: " + ChatColor.GOLD + "/" + label + " <player> <home>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            sender.sendMessage(ChatColor.RED + "Unknown player: " + ChatColor.YELLOW + args[0]);
            return true;
        }

        final UUID ownerId = target.getUniqueId();
        final String homeName = normalize(args[1]);

        // 1) Find which server owns <owner>/<home>
        String targetServer = resolveHomeServer(ownerId, homeName);
        if (targetServer == null) targetServer = homes.localServer();
        final String localServer = homes.localServer();

        // 2) If it's local, teleport directly
        if (targetServer.equalsIgnoreCase(localServer)) {
            Location loc = homes.getHome(ownerId, homeName);
            if (loc == null) {
                sender.sendMessage(ChatColor.RED + "Home not found: " + ChatColor.YELLOW + args[1]);
                return true;
            }
            World w = loc.getWorld();
            if (w == null) {
                sender.sendMessage(ChatColor.RED + "World not loaded: " + ChatColor.YELLOW + (loc.getWorld() != null ? loc.getWorld().getName() : "null"));
                return true;
            }
            admin.teleport(loc);
            sender.sendMessage(ChatColor.GREEN + "Teleported to " + ChatColor.AQUA
                    + (target.getName() != null ? target.getName() : ownerId) + ChatColor.GREEN
                    + "’s home " + ChatColor.AQUA + homeName + ChatColor.GREEN + ".");
            return true;
        }

        // 3) Cross-server disabled?
        var cs = plugin.getCrossServerSettings();
        if (!cs.homes()) {
            sender.sendMessage(ChatColor.RED + "Cross-server homes are disabled by server config.");
            sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.AQUA + "/server " + targetServer
                    + ChatColor.GRAY + " then run " + ChatColor.AQUA + "/otherhome "
                    + (target.getName() != null ? target.getName() : ownerId) + " " + homeName);
            return true;
        }

        // 4) Publish to the target server’s INDIVIDUAL channel (same as /home)
        PacketManager pm = plugin.getPacketManager();
        if (pm != null && pm.isInitialized()) {
            final String requestId = java.util.UUID.randomUUID().toString();

            OtherHomeTeleportRequestPacket pkt = new OtherHomeTeleportRequestPacket(
                    admin.getUniqueId(), ownerId, homeName, targetServer, requestId
            );

            PacketChannel channel = PacketChannel.individual(targetServer);
            pm.sendPacket(channel, pkt);

            plugin.getLogger().info("[HOME/SEND-OTHER] from=" + localServer
                    + " subject=" + admin.getUniqueId()
                    + " owner=" + ownerId
                    + " home=" + homeName
                    + " -> target=" + targetServer
                    + " requestId=" + requestId);
        } else {
            sender.sendMessage(ChatColor.RED + "Cross-server messaging is disabled. Ask an admin to enable RabbitMQ.");
            sender.sendMessage(ChatColor.GRAY + "You can still switch with " + ChatColor.AQUA + "/server " + targetServer
                    + ChatColor.GRAY + " and run " + ChatColor.AQUA + "/otherhome "
                    + (target.getName() != null ? target.getName() : ownerId) + " " + homeName);
            return true;
        }

        // 5) Switch the admin (subject) to the target server (same as /home)
        if (sendPlayerToServer(admin, targetServer)) {
            sender.sendMessage(ChatColor.YELLOW + "Sending you to " + ChatColor.AQUA + targetServer
                    + ChatColor.YELLOW + "… you’ll be teleported to "
                    + ChatColor.AQUA + (target.getName() != null ? target.getName() : ownerId)
                    + ChatColor.YELLOW + "’s home " + ChatColor.AQUA + homeName + ChatColor.YELLOW + " on arrival.");
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to switch you to " + targetServer + ".");
            sender.sendMessage(ChatColor.GRAY + "Make sure that server name matches your proxy config.");
        }
        return true;
    }

    /* ---------- tab completion ---------- */

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player)) return List.of();
        if (!sender.hasPermission("oreo.homes.other")) return List.of();

        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (target == null) return List.of();
            return safeHomesOf(target.getUniqueId()).stream()
                    .filter(h -> h.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    /* ---------- helpers ---------- */

    private Set<String> safeHomesOf(UUID owner) {
        try {
            Set<String> s = homes.homes(owner);
            return (s == null) ? Collections.emptySet() : s;
        } catch (Throwable t) { return Collections.emptySet(); }
    }

    private String resolveHomeServer(UUID ownerId, String homeName) {
        // Prefer dedicated method if your HomeService has it:
        try {
            Method m = homes.getClass().getMethod("homeServer", UUID.class, String.class);
            Object v = m.invoke(homes, ownerId, homeName);
            if (v != null) return v.toString();
        } catch (NoSuchMethodException ignored) {
            // fall back to listHomes/getHomes + getServer()
            try {
                Method m = homes.getClass().getMethod("listHomes", UUID.class);
                Object res = m.invoke(homes, ownerId);
                String srv = extractServerFromMap(res, homeName);
                if (srv != null) return srv;
            } catch (NoSuchMethodException ignored2) {
                try {
                    Method m = homes.getClass().getMethod("getHomes", UUID.class);
                    Object res = m.invoke(homes, ownerId);
                    String srv = extractServerFromMap(res, homeName);
                    if (srv != null) return srv;
                } catch (Throwable ignored3) {}
            } catch (Throwable ignored4) {}
        } catch (Throwable ignored) {}

        return null; // unknown -> let caller treat as local
    }

    @SuppressWarnings("unchecked")
    private String extractServerFromMap(Object mapObj, String homeName) {
        if (!(mapObj instanceof Map<?, ?> map)) return null;
        Object dto = map.get(homeName.toLowerCase(Locale.ROOT));
        if (dto == null) return null;
        try {
            Method gs = dto.getClass().getMethod("getServer");
            Object v  = gs.invoke(dto);
            return v == null ? null : v.toString();
        } catch (Throwable ignored) { return null; }
    }

    private static String normalize(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }

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
