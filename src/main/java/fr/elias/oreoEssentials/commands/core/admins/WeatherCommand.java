package fr.elias.oreoEssentials.commands.core.admins;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public final class WeatherCommand implements TabExecutor {

    enum WType { SUN, RAIN, STORM }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("oreo.weather")) {
            sender.sendMessage("§cYou don't have permission (oreo.weather).");
            return true;
        }

        // Normalize to main /weather signature
        WType type;
        List<String> list = new ArrayList<>(Arrays.asList(args));
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "sun":   type = WType.SUN;   break;
            case "rain":  type = WType.RAIN;  break;
            case "storm": type = WType.STORM; break;
            default:
                if (args.length == 0) {
                    help(sender, label);
                    return true;
                }
                String t = args[0].toLowerCase(Locale.ROOT);
                if (t.equals("sun")) type = WType.SUN;
                else if (t.equals("rain")) type = WType.RAIN;
                else if (t.equals("storm")) type = WType.STORM;
                else { help(sender, label); return true; }
                list.remove(0);
        }

        // Parse optional arg2 = "lock" | <seconds> | world
        boolean lock = false;
        Integer seconds = null;
        String worldArg = null;

        if (!list.isEmpty()) {
            String a = list.get(0);
            if (a.equalsIgnoreCase("lock")) {
                lock = true; list.remove(0);
            } else {
                Integer s = tryParseInt(a);
                if (s != null && s > 0) {
                    seconds = s; list.remove(0);
                }
            }
        }
        if (!list.isEmpty()) {
            worldArg = list.get(0);
        }

        List<World> targets = resolveWorlds(sender, worldArg);
        if (targets.isEmpty()) {
            sender.sendMessage("§cNo target worlds resolved.");
            return true;
        }

        int ticks = seconds == null ? 0 : Math.max(1, seconds) * 20;
        int changed = 0;

        for (World w : targets) {
            // Apply weather state
            switch (type) {
                case SUN:
                    w.setStorm(false);
                    w.setThundering(false);
                    break;
                case RAIN:
                    w.setStorm(true);
                    w.setThundering(false);
                    break;
                case STORM:
                    w.setStorm(true);
                    w.setThundering(true);
                    break;
            }

            // Duration handling
            if (seconds != null) {
                w.setWeatherDuration(ticks);
                if (type == WType.STORM) {
                    w.setThunderDuration(ticks);
                }
            }

            // Lock handling via gamerule
            if (lock) {
                try {
                    w.setGameRuleValue("doWeatherCycle", "false");
                } catch (Throwable ignored) {}
            }

            changed++;
        }

        String labelWorld = targets.size() == 1 ? targets.get(0).getName() : "multiple worlds (" + targets.size() + ")";
        String summary = "§aWeather set to §f" + type.name().toLowerCase(Locale.ROOT)
                + (seconds != null ? " §7for §f" + seconds + "s" : "")
                + (lock ? " §8(locked)" : "")
                + " §7in §f" + labelWorld + "§7.";
        sender.sendMessage(summary);

        if (lock && targets.size() == 1) {
            sender.sendMessage("§8Tip: /weather " + type.name().toLowerCase(Locale.ROOT) + " unlock " + targets.get(0).getName());
        }
        return true;
    }

    private static void help(CommandSender s, String label) {
        s.sendMessage("§eUsage:");
        s.sendMessage("§f/" + label + " <sun|rain|storm> [lock|seconds] [world|all]");
        s.sendMessage("§7Examples: §f/sun §8| §f/rain §8| §f/storm §8| §f/sun lock §8| §f/sun 120 §8| §f/sun world");
    }

    private static Integer tryParseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private static List<World> resolveWorlds(CommandSender sender, String arg) {
        if (arg == null || arg.isEmpty()) {
            if (sender instanceof Player) return Collections.singletonList(((Player) sender).getWorld());
            return Bukkit.getWorlds(); // console -> all
        }
        if (arg.equalsIgnoreCase("all")) return Bukkit.getWorlds();
        World w = Bukkit.getWorld(arg);
        return w == null ? Collections.emptyList() : Collections.singletonList(w);
    }

    // ---- Tab complete ----
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("oreo.weather")) return Collections.emptyList();

        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("weather")) {
            if (args.length == 1) {
                return filter(Arrays.asList("sun","rain","storm"), args[0]);
            }
            if (args.length == 2) {
                // Could be "lock" | seconds | world
                List<String> pool = new ArrayList<>();
                pool.add("lock");
                pool.addAll(Arrays.asList("60","120","300","600"));
                pool.addAll(worldsPlusAll());
                return filter(pool, args[1]);
            }
            if (args.length == 3) {
                return filter(worldsPlusAll(), args[2]);
            }
            return Collections.emptyList();
        } else {
            // /sun /rain /storm
            if (args.length == 1) {
                List<String> pool = new ArrayList<>();
                pool.add("lock");
                pool.addAll(Arrays.asList("60","120","300","600"));
                pool.addAll(worldsPlusAll());
                return filter(pool, args[0]);
            }
            if (args.length == 2) {
                return filter(worldsPlusAll(), args[1]);
            }
        }
        return Collections.emptyList();
    }

    private static List<String> worldsPlusAll() {
        List<String> ws = Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
        ws.add("all");
        return ws;
    }

    private static List<String> filter(List<String> pool, String pref) {
        String p = pref == null ? "" : pref.toLowerCase(Locale.ROOT);
        return pool.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }
}

