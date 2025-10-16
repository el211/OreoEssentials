// File: src/main/java/fr/elias/oreoEssentials/commands/core/admins/OtherHomeCommand.java
package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.homes.TeleportBroker;
import fr.elias.oreoEssentials.services.HomeService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class OtherHomeCommand implements CommandExecutor, TabCompleter {

    private final OreoEssentials plugin;
    private final HomeService homeService;

    public OtherHomeCommand(OreoEssentials plugin, HomeService homeService) {
        this.plugin = plugin;
        this.homeService = homeService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("oreo.homes.other")) {
            sender.sendMessage("§cYou don’t have permission.");
            return true;
        }
        if (!(sender instanceof Player admin)) {
            sender.sendMessage("§cOnly players can use this.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§eUsage: §6/" + label + " <player> [home]");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            sender.sendMessage("§cUnknown player: §e" + args[0]);
            return true;
        }
        UUID owner = target.getUniqueId();

        Map<String, Stored> homes = fetchHomes(owner);
        if (homes.isEmpty()) {
            sender.sendMessage("§7" + (target.getName() != null ? target.getName() : owner) + " §7has no homes.");
            return true;
        }

        String chosenName;
        if (args.length >= 2) {
            chosenName = args[1].toLowerCase(Locale.ROOT);
            if (!homes.containsKey(chosenName)) {
                sender.sendMessage("§cHome not found: §e" + args[1] + "§7. Available: §f" + String.join("§7, §f", homes.keySet()));
                return true;
            }
        } else if (homes.size() == 1) {
            chosenName = homes.keySet().iterator().next();
        } else {
            sender.sendMessage("§eSpecify a home. Available: §f" + String.join("§7, §f", homes.keySet()));
            return true;
        }

        Stored h = homes.get(chosenName);

        // Normalize server names
        final String localServer = plugin.getConfigService().serverName();
        final String bukkitServer = Bukkit.getServer().getName();
        final String homeServer = (h.server == null || h.server.isBlank()) ? localServer : h.server;

        // Same-server: if it matches either canonical server id OR Bukkit server name
        if (homeServer.equalsIgnoreCase(localServer) || homeServer.equalsIgnoreCase(bukkitServer)) {
            World w = Bukkit.getWorld(h.world);
            if (w == null) {
                sender.sendMessage("§cWorld not loaded: §e" + h.world);
                return true;
            }
            Location loc = new Location(w, h.x, h.y, h.z);
            admin.teleport(loc); // avoid compile dependency on TeleportService
            sender.sendMessage("§aTeleported to §b" + (target.getName() != null ? target.getName() : owner) + "§a’s home §b" + chosenName + "§a.");
            return true;
        }

        // Cross-server: request via TeleportBroker
        TeleportBroker broker = plugin.getTeleportBroker();
        if (broker == null) {
            sender.sendMessage("§cCross-server homes are disabled or broker unavailable.");
            return true;
        }

        boolean queued = broker.requestTeleportOtherHome(admin.getUniqueId(), owner, chosenName);
        if (queued) {
            sender.sendMessage("§aCross-server teleport requested to §b" + homeServer + "§a. You may be moved shortly.");
        } else {
            sender.sendMessage("§cFailed to queue cross-server teleport (couldn’t resolve target server).");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("oreo.homes.other")) return Collections.emptyList();

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
            if (target == null) return Collections.emptyList();
            Map<String, Stored> homes = fetchHomes(target.getUniqueId());
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return homes.keySet().stream()
                    .filter(h -> h.startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /* ---------------- Helpers ---------------- */

    @SuppressWarnings("unchecked")
    private Map<String, Stored> fetchHomes(UUID owner) {
        Object mapObj = null;

        try {
            Method m = homeService.getClass().getMethod("listHomes", UUID.class);
            mapObj = m.invoke(homeService, owner);
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable t) {
            plugin.getLogger().fine("[OtherHome] listHomes reflect failed: " + t.getMessage());
        }

        if (mapObj == null) {
            try {
                Method m = homeService.getClass().getMethod("getHomes", UUID.class);
                mapObj = m.invoke(homeService, owner);
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable t) {
                plugin.getLogger().fine("[OtherHome] getHomes reflect failed: " + t.getMessage());
            }
        }

        if (!(mapObj instanceof Map)) return Collections.emptyMap();
        Map<?, ?> raw = (Map<?, ?>) mapObj;

        final String localServer = plugin.getConfigService().serverName();
        final String bukkitServer = Bukkit.getServer().getName();

        Map<String, Stored> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            String name = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
            Object h = e.getValue();
            try {
                Method mWorld  = h.getClass().getMethod("getWorld");
                Method mX      = h.getClass().getMethod("getX");
                Method mY      = h.getClass().getMethod("getY");
                Method mZ      = h.getClass().getMethod("getZ");
                Method mServer = null;
                try { mServer = h.getClass().getMethod("getServer"); } catch (NoSuchMethodException ignored) {}

                String world = (String) mWorld.invoke(h);
                double x = ((Number) mX.invoke(h)).doubleValue();
                double y = ((Number) mY.invoke(h)).doubleValue();
                double z = ((Number) mZ.invoke(h)).doubleValue();

                String srv = null;
                if (mServer != null) srv = (String) mServer.invoke(h);
                // default + normalize
                if (srv == null || srv.isBlank()) srv = localServer;
                if (srv.equalsIgnoreCase(bukkitServer)) srv = localServer;

                out.put(name, new Stored(world, x, y, z, srv));
            } catch (Throwable ex) {
                plugin.getLogger().fine("[OtherHome] Bad home entry for " + name + ": " + ex.getMessage());
            }
        }
        return out;
    }

    private static final class Stored {
        final String world;
        final double x, y, z;
        final String server;
        Stored(String world, double x, double y, double z, String server) {
            this.world = world; this.x = x; this.y = y; this.z = z; this.server = server;
        }
    }
}
