// File: src/main/java/fr/elias/oreoEssentials/commands/core/admins/OtherHomesListCommand.java
package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.services.HomeService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class OtherHomesListCommand implements CommandExecutor, TabCompleter {

    private final OreoEssentials plugin;
    private final HomeService homeService;

    public OtherHomesListCommand(OreoEssentials plugin, HomeService homeService) {
        this.plugin = plugin;
        this.homeService = homeService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("oreo.homes.other")) {
            sender.sendMessage("§cYou don’t have permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§eUsage: §6/" + label + " <player>");
            return true;
        }

        // --- resolve the RIGHT UUID for the target name ---
        final String inputName = args[0];
        final UUID owner = resolveTargetUUID(inputName);
        if (owner == null) {
            sender.sendMessage("§cUnknown player: §e" + inputName);
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(owner);

        Map<String, HomeInfo> homes = fetchHomesReflectively(owner);
        if (homes.isEmpty()) {
            sender.sendMessage("§7" + (target.getName() != null ? target.getName() : owner) + " §7has no homes.");
            return true;
        }

        sender.sendMessage("§6Homes of §e" + (target.getName() != null ? target.getName() : owner) + "§6:");
        for (var e : homes.entrySet()) {
            HomeInfo h = e.getValue();
            sender.sendMessage(String.format(
                    " §8- §b%s§7 @ §f%s§7 (%.1f, %.1f, %.1f) §8[server: %s]",
                    e.getKey(), h.world, h.x, h.y, h.z, h.server
            ));
        }
        sender.sendMessage("§7Tip: §e/otherhome " + (target.getName() != null ? target.getName() : owner) + " <home>");
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
        return Collections.emptyList();
    }

    /* ---------------- UUID resolution ---------------- */

    /**
     * Resolve the UUID for a player name using:
     * 1) exact online player
     * 2) PlayerDirectory (if present) via reflection method lookupUuidByName(String)
     * 3) Bukkit offline player fallback
     */
    private UUID resolveTargetUUID(String inputName) {
        if (inputName == null || inputName.isEmpty()) return null;

        // 1) exact online
        Player online = Bukkit.getPlayerExact(inputName);
        if (online != null) return online.getUniqueId();

        // 2) try PlayerDirectory if Oreo has it
        try {
            Object dir = plugin.getPlayerDirectory(); // method exists in OreoEssentials
            if (dir != null) {
                try {
                    Method m = dir.getClass().getMethod("lookupUuidByName", String.class);
                    Object res = m.invoke(dir, inputName);
                    if (res instanceof UUID) return (UUID) res;
                } catch (NoSuchMethodException ignored) {
                    // directory present but doesn’t expose helper; fall through
                } catch (Throwable t) {
                    plugin.getLogger().warning("[OtherHomesList] PlayerDirectory lookup failed: " + t.getMessage());
                }
            }
        } catch (Throwable ignored) {
            // getPlayerDirectory not present – continue
        }

        // 3) Bukkit fallback (may be offline UUID on some setups)
        try {
            return Bukkit.getOfflinePlayer(inputName).getUniqueId();
        } catch (Throwable t) {
            return null;
        }
    }

    /* ---------------- Homes fetch (reflection-only) ---------------- */

    @SuppressWarnings("unchecked")
    private Map<String, HomeInfo> fetchHomesReflectively(UUID owner) {
        if (owner == null) return Collections.emptyMap();

        // Try listHomes(UUID)
        try {
            Method m = HomeService.class.getMethod("listHomes", UUID.class);
            Object result = m.invoke(homeService, owner);
            return convertResultToMap(result);
        } catch (NoSuchMethodException ignored) {
            // Try getHomes(UUID)
            try {
                Method m = HomeService.class.getMethod("getHomes", UUID.class);
                Object result = m.invoke(homeService, owner);
                return convertResultToMap(result);
            } catch (NoSuchMethodException e2) {
                plugin.getLogger().warning("[OtherHomesList] HomeService has neither listHomes(UUID) nor getHomes(UUID).");
            } catch (Throwable t) {
                plugin.getLogger().warning("[OtherHomesList] getHomes(UUID) failed: " + t.getMessage());
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[OtherHomesList] listHomes(UUID) failed: " + t.getMessage());
        }
        return Collections.emptyMap();
    }

    private Map<String, HomeInfo> convertResultToMap(Object result) {
        if (!(result instanceof Map<?, ?> map)) return Collections.emptyMap();

        Map<String, HomeInfo> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String name = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
            HomeInfo hi = readDto(entry.getValue(), name);
            if (hi != null) out.put(name, hi);
        }
        return out;
    }

    private HomeInfo readDto(Object dto, String name) {
        if (dto == null) return null;
        try {
            // Expected getters: getWorld(), getX(), getY(), getZ(), optional getServer()
            String world  = invokeString(dto, "getWorld", "world");
            double x      = invokeDouble(dto, "getX", 0.0);
            double y      = invokeDouble(dto, "getY", 0.0);
            double z      = invokeDouble(dto, "getZ", 0.0);
            String server = tryInvokeString(dto, "getServer");
            if (server == null || server.isBlank()) server = Bukkit.getServer().getName();
            return new HomeInfo(name, world, x, y, z, server);
        } catch (Throwable t) {
            plugin.getLogger().warning("[OtherHomesList] DTO read failed for '" + name + "': " + t.getMessage());
            return null;
        }
    }

    private String tryInvokeString(Object o, String method) {
        try { return invokeString(o, method, null); } catch (Throwable ignored) { return null; }
    }

    private String invokeString(Object o, String method, String def) throws Exception {
        Object v = o.getClass().getMethod(method).invoke(o);
        return v != null ? v.toString() : def;
    }

    private double invokeDouble(Object o, String method, double def) throws Exception {
        Object v = o.getClass().getMethod(method).invoke(o);
        return (v instanceof Number n) ? n.doubleValue() : def;
    }

    /* ---------------- Model ---------------- */

    private static final class HomeInfo {
        final String name;
        final String world;
        final double x, y, z;
        final String server;
        HomeInfo(String name, String world, double x, double y, double z, String server) {
            this.name = name; this.world = world; this.x = x; this.y = y; this.z = z; this.server = server;
        }
    }
}
