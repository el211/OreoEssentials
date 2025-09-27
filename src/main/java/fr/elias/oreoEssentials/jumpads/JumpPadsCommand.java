package fr.elias.oreoEssentials.jumpads;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Locale;
import java.util.stream.Collectors;

public class JumpPadsCommand implements CommandExecutor, TabCompleter {
    private final JumpPadsManager mgr;

    public JumpPadsCommand(JumpPadsManager mgr) {
        this.mgr = mgr;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        // Basic permission gate (optional: adjust node name to your plugin.yml)
        if (!sender.hasPermission("oreo.jumpad")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }

        if (a.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "JumpPads: " + String.join(", ", mgr.listNames()));
            sender.sendMessage(ChatColor.YELLOW + "Usage:");
            sender.sendMessage(ChatColor.YELLOW + "  /" + label + " create <name> [power] [upward] [useLookDir]  (block under you)");
            sender.sendMessage(ChatColor.YELLOW + "  /" + label + " remove <name>");
            sender.sendMessage(ChatColor.YELLOW + "  /" + label + " list");
            sender.sendMessage(ChatColor.YELLOW + "  /" + label + " info <name>");
            return true;
        }

        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(ChatColor.RED + "Players only.");
                    return true;
                }
                if (a.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " create <name> [power] [upward] [useLookDir]");
                    sender.sendMessage(ChatColor.GRAY + "Defaults → power=" + mgr.defaultPower
                            + ", upward=" + mgr.defaultUpward + ", useLookDir=" + mgr.defaultUseLookDir);
                    return true;
                }
                String name = a[1];

                // Use manager defaults (config-backed) when args omitted
                double power  = (a.length >= 3) ? parseDouble(a[2], mgr.defaultPower) : mgr.defaultPower;
                double upward = (a.length >= 4) ? parseDouble(a[3], mgr.defaultUpward) : mgr.defaultUpward;
                boolean look  = (a.length >= 5) ? parseBool(a[4], mgr.defaultUseLookDir) : mgr.defaultUseLookDir;

                Location under = p.getLocation().clone().subtract(0, 1, 0);
                boolean ok = mgr.create(name, under, power, upward, look);
                if (ok) {
                    sender.sendMessage(ChatColor.GREEN + "JumpPad '" + name + "' created at "
                            + ChatColor.AQUA + under.getBlockX() + "," + under.getBlockY() + "," + under.getBlockZ()
                            + ChatColor.GREEN + " (power=" + power + ", upward=" + upward + ", look=" + look + ")");
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to create (invalid location or name).");
                }
                return true;
            }

            case "remove" -> {
                if (a.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " remove <name>");
                    return true;
                }
                boolean ok = mgr.remove(a[1]);
                sender.sendMessage(ok ? ChatColor.GREEN + "Removed." : ChatColor.RED + "Not found.");
                return true;
            }

            case "list" -> {
                sender.sendMessage(ChatColor.GOLD + "JumpPads: " + String.join(", ", mgr.listNames()));
                return true;
            }

            case "info" -> {
                if (a.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " info <name>");
                    return true;
                }
                JumpPadsManager.JumpPad jp = mgr.getByName(a[1]);
                if (jp == null) {
                    sender.sendMessage(ChatColor.RED + "JumpPad not found.");
                    return true;
                }
                sender.sendMessage(ChatColor.AQUA + "Name: " + jp.name);
                sender.sendMessage(ChatColor.AQUA + "World: " + jp.world.getName()
                        + "  xyz: " + jp.x + " " + jp.y + " " + jp.z);
                sender.sendMessage(ChatColor.AQUA + "Power: " + jp.power
                        + "  Upward: " + jp.upward
                        + "  UseLookDir: " + jp.useLookDir);
                return true;
            }

            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Try /" + label);
                return true;
            }
        }
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }
    private boolean parseBool(String s, boolean def) {
        if (s == null) return def;
        return s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("y");
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] a) {
        if (!sender.hasPermission("oreo.jumpad")) return java.util.Collections.emptyList();

        if (a.length == 1) return java.util.List.of("create", "remove", "list", "info");

        if (a.length == 2) {
            if (a[0].equalsIgnoreCase("remove") || a[0].equalsIgnoreCase("info")) {
                String prefix = a[1].toLowerCase(Locale.ROOT);
                return mgr.listNames().stream()
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .collect(Collectors.toList());
            }
        }

        // Suggest booleans for useLookDir
        if (a.length == 5 && a[0].equalsIgnoreCase("create")) {
            return new ArrayList<>(java.util.List.of("true", "false"));
        }

        return java.util.Collections.emptyList();
    }
}
