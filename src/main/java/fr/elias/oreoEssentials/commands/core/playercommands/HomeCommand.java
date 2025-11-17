// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/HomeCommand.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.HomeTeleportRequestPacket;
import fr.elias.oreoEssentials.services.HomeService;
import fr.elias.oreoEssentials.util.Lang;
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

        // /home or /home list -> show homes (CROSS-SERVER)
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            List<String> names = crossServerNames(p.getUniqueId());
            if (names.isEmpty()) {
                Lang.send(p, "home.no-homes",
                        Map.of("sethome", "/sethome"),
                        p
                );
                return true;
            }

            String list = names.stream()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.joining(ChatColor.GRAY + ", " + ChatColor.AQUA));

            Lang.send(p, "home.list",
                    Map.of("homes", list),
                    p
            );
            Lang.send(p, "home.tip",
                    Map.of("label", label),
                    p
            );
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
                Lang.send(p, "home.not-found",
                        Map.of("name", raw),
                        p
                );
                suggestClosest(p, key);
                return true;
            }
            p.teleport(loc);
            Lang.send(p, "home.teleported",
                    Map.of("name", key),
                    p
            );
            return true;
        }

        // Respect cross-server toggle for homes
        var cs = OreoEssentials.get().getCrossServerSettings();
        if (!cs.homes()) {
            Lang.send(p, "home.cross-disabled",
                    Collections.emptyMap(),
                    p
            );
            Lang.send(p, "home.cross-disabled-tip",
                    Map.of(
                            "server", targetServer,
                            "name", key
                    ),
                    p
            );
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

            HomeTeleportRequestPacket pkt = new HomeTeleportRequestPacket(p.getUniqueId(), key, targetServer, requestId);
            PacketChannel targetChannel = PacketChannel.individual(targetServer);
            pm.sendPacket(targetChannel, pkt);
        } else {
            Lang.send(p, "home.messaging-disabled",
                    Collections.emptyMap(),
                    p
            );
            Lang.send(p, "home.messaging-disabled-tip",
                    Map.of(
                            "server", targetServer,
                            "name", key
                    ),
                    p
            );
            return true;
        }

        // Switch via proxy
        boolean switched = sendPlayerToServer(p, targetServer);
        if (switched) {
            Lang.send(p, "home.sending",
                    Map.of(
                            "server", targetServer,
                            "name", key
                    ),
                    p
            );
        } else {
            Lang.send(p, "home.switch-failed",
                    Map.of("server", targetServer),
                    p
            );
            Lang.send(p, "home.switch-failed-tip",
                    Map.of("server", targetServer),
                    p
            );
        }
        return true;
    }

    /* ---------------- tab completion ---------------- */

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player p)) return List.of();
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return crossServerNames(p.getUniqueId()).stream()
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(partial))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    /* ---------------- helpers ---------------- */

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    /** Cross-server names (plain names). Falls back to empty if unavailable. */
    private List<String> crossServerNames(UUID id) {
        try {
            // Preferred: use aggregated list from HomeService
            Set<String> set = homes.allHomeNames(id);
            if (set != null) {
                return set.stream()
                        .filter(Objects::nonNull)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
            }
        } catch (Throwable ignored) {}
        // Very last resort: empty (do not read local-only to avoid inconsistency with /homes)
        return Collections.emptyList();
    }

    private void suggestClosest(Player p, String key) {
        List<String> suggestions = crossServerNames(p.getUniqueId()).stream()
                .filter(n -> n.toLowerCase(Locale.ROOT).contains(key.toLowerCase(Locale.ROOT)))
                .limit(5)
                .collect(Collectors.toList());
        if (!suggestions.isEmpty()) {
            String joined = String.join(
                    ChatColor.GRAY + ", " + ChatColor.AQUA,
                    suggestions
            );
            Lang.send(p, "home.suggest",
                    Map.of("suggestions", joined),
                    p
            );
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
