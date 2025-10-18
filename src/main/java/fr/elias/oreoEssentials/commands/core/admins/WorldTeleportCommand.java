// File: src/main/java/fr/elias/oreoEssentials/worlds/WorldTeleportCommand.java
package fr.elias.oreoEssentials.commands.core.admins;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class WorldTeleportCommand implements TabExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("oreo.world")) {
            sender.sendMessage("§cYou don't have permission (oreo.world).");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§eUsage: §f/world <name|normal|nether|end|index> [playerName] [-s]");
            sender.sendMessage("§7Examples: §f/world world_nether  §8|  §f/world nether  §8|  §f/world 2  §8|  §f/world end Steve -s");
            return true;
        }

        // Parse flags
        boolean silent = hasFlag(args, "-s");

        // Resolve target player
        Player target = null;
        if (args.length >= 2) {
            // If console -> playerName required (unless just -s)
            if (!(sender instanceof Player)) {
                String candidate = firstNonFlag(args, 1);
                if (candidate == null) {
                    sender.sendMessage("§cConsole usage needs a target player: §f/world <name> <playerName> [-s]");
                    return true;
                }
                target = Bukkit.getPlayerExact(candidate);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found: §f" + candidate);
                    return true;
                }
            } else {
                // Player sender: optional target if an online name is provided
                String candidate = firstNonFlag(args, 1);
                if (candidate != null) {
                    target = Bukkit.getPlayerExact(candidate);
                    if (target == null) {
                        sender.sendMessage("§cPlayer not found: §f" + candidate);
                        return true;
                    }
                }
            }
        }
        if (target == null) {
            if (sender instanceof Player) {
                target = (Player) sender;
            } else {
                sender.sendMessage("§cConsole must specify a target player: §f/world <name> <playerName> [-s]");
                return true;
            }
        }

        // Resolve destination world by name/alias/index
        World dest = resolveWorld(args[0], target);
        if (dest == null) {
            sender.sendMessage("§cWorld not found / unsupported: §f" + args[0]);
            return true;
        }

        // Per-world permission: cmi.command.world.<worldName>
        String perWorldPerm = "cmi.command.world." + dest.getName();
        if (!sender.hasPermission(perWorldPerm)) {
            sender.sendMessage("§cYou lack permission: §f" + perWorldPerm);
            return true;
        }

        // Teleport to a safe-ish spot: world spawn (with highest block correction)
        Location loc = safeSpawn(dest);

        boolean ok = target.teleport(loc);
        if (!ok) {
            sender.sendMessage("§cTeleport failed.");
            return true;
        }

        if (!silent) {
            if (sender != target) {
                target.sendMessage("§aYou were teleported to §f" + dest.getName() + " §7by §f" + sender.getName());
            } else {
                target.sendMessage("§aTeleported to §f" + dest.getName());
            }
        }
        sender.sendMessage("§aTeleported §f" + target.getName() + " §7to §f" + dest.getName());
        return true;
    }

    // ---------- Helpers ----------

    private static boolean hasFlag(String[] args, String flag) {
        for (String a : args) if (flag.equalsIgnoreCase(a)) return true;
        return false;
    }

    private static String firstNonFlag(String[] args, int startIdx) {
        for (int i = startIdx; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("-")) continue;
            return a;
        }
        return null;
    }

    private static Location safeSpawn(World w) {
        Location spawn = w.getSpawnLocation().clone();
        try {
            int x = spawn.getBlockX();
            int z = spawn.getBlockZ();
            int y = w.getHighestBlockYAt(x, z);
            if (y > 0) spawn.setY(y + 1);
        } catch (Throwable ignored) {}
        return spawn;
    }

    private static World resolveWorld(String token, Player contextPlayer) {
        // Accept exact name first
        World byName = Bukkit.getWorld(token);
        if (byName != null) return byName;

        String low = token.toLowerCase(Locale.ROOT);

        // If numeric: index into loaded worlds (1-based)
        if (low.matches("\\d+")) {
            int idx = Integer.parseInt(low);
            List<World> all = Bukkit.getWorlds();
            if (idx >= 1 && idx <= all.size()) return all.get(idx - 1);
        }

        // Aliases normal/nether/end relative to a base world
        World base = (contextPlayer != null ? contextPlayer.getWorld() : (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0)));
        if (base == null) return null;
        String baseName = stripDimSuffix(base.getName()); // e.g., "world" from "world", "world_nether", "world_the_end"
        switch (low) {
            case "normal":
            case "overworld":
            case "daylight":
            {
                World w = Bukkit.getWorld(baseName);
                if (w != null) return w;
            }
            break;
            case "nether":
            {
                World w = Bukkit.getWorld(baseName + "_nether");
                if (w == null) w = firstByEnvironment(Environment.NETHER);
                if (w != null) return w;
            }
            break;
            case "end":
            case "the_end":
            {
                World w = Bukkit.getWorld(baseName + "_the_end");
                if (w == null) w = firstByEnvironment(Environment.THE_END);
                if (w != null) return w;
            }
            break;
        }

        // Fuzzy: try case-insensitive match among loaded worlds
        for (World w : Bukkit.getWorlds()) {
            if (w.getName().equalsIgnoreCase(token)) return w;
        }

        return null;
    }

    private static String stripDimSuffix(String name) {
        if (name.endsWith("_nether")) return name.substring(0, name.length() - "_nether".length());
        if (name.endsWith("_the_end")) return name.substring(0, name.length() - "_the_end".length());
        return name;
    }

    private static World firstByEnvironment(Environment env) {
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == env) return w;
        }
        return null;
    }

    // ---------- Tab Complete ----------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("oreo.world")) return Collections.emptyList();

        if (args.length == 1) {
            List<String> names = Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
            names.addAll(Arrays.asList("normal", "nether", "end"));
            // Also offer numeric indices
            names.addAll(IntStream.rangeClosed(1, Math.max(3, Bukkit.getWorlds().size()))
                    .mapToObj(String::valueOf).collect(Collectors.toList()));
            return filter(names, args[0]);
        }

        if (args.length == 2) {
            List<String> pool = new ArrayList<>();
            pool.add("-s");
            pool.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
            return filter(pool, args[1]);
        }

        if (args.length == 3) {
            return filter(Collections.singletonList("-s"), args[2]);
        }

        return Collections.emptyList();
    }

    private static List<String> filter(List<String> pool, String pref) {
        String p = pref == null ? "" : pref.toLowerCase(Locale.ROOT);
        return pool.stream().distinct().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }
}
