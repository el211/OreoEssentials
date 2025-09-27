package fr.elias.oreoEssentials.portals;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.stream.Collectors;

public class PortalsCommand implements CommandExecutor, TabCompleter {

    private final PortalsManager manager;

    public PortalsCommand(PortalsManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (a.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "Portals: " + String.join(", ", manager.listNames()));
            sender.sendMessage(ChatColor.YELLOW + "Usage: /portal pos1 | pos2 | create <name> <world> <x> <y> <z> [keepYaw] | remove <name> | list");
            return true;
        }

        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "pos1" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(ChatColor.RED + "Players only."); return true; }
                manager.setPos1(p, p.getLocation());
                sender.sendMessage(ChatColor.GREEN + "Portal pos1 set at " + locStr(p.getLocation()));
                return true;
            }
            case "pos2" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(ChatColor.RED + "Players only."); return true; }
                manager.setPos2(p, p.getLocation());
                sender.sendMessage(ChatColor.GREEN + "Portal pos2 set at " + locStr(p.getLocation()));
                return true;
            }
            case "create" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(ChatColor.RED + "Players only."); return true; }
                if (a.length < 6) {
                    sender.sendMessage(ChatColor.RED + "Usage: /portal create <name> <world> <x> <y> <z> [keepYaw]");
                    return true;
                }
                String name = a[1];
                World w = Bukkit.getWorld(a[2]);
                if (w == null) { sender.sendMessage(ChatColor.RED + "World not found: " + a[2]); return true; }
                double x, y, z;
                try { x = Double.parseDouble(a[3]); y = Double.parseDouble(a[4]); z = Double.parseDouble(a[5]); }
                catch (NumberFormatException ex) { sender.sendMessage(ChatColor.RED + "Invalid coords."); return true; }
                boolean keepYaw = a.length >= 7 && (a[6].equalsIgnoreCase("true") || a[6].equalsIgnoreCase("yes"));
                Location dest = new Location(w, x, y, z, p.getLocation().getYaw(), p.getLocation().getPitch());

                boolean ok = manager.create(name, p, dest, keepYaw);
                if (!ok) sender.sendMessage(ChatColor.RED + "Failed. Make sure pos1/pos2 are set in the same world.");
                else sender.sendMessage(ChatColor.GREEN + "Created portal " + ChatColor.AQUA + name + ChatColor.GREEN + " -> " + locStr(dest));
                return true;
            }
            case "remove" -> {
                if (a.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /portal remove <name>"); return true; }
                boolean ok = manager.remove(a[1]);
                sender.sendMessage(ok ? ChatColor.GREEN + "Removed." : ChatColor.RED + "Portal not found.");
                return true;
            }
            case "list" -> {
                sender.sendMessage(ChatColor.GOLD + "Portals: " + String.join(", ", manager.listNames()));
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
                return true;
            }
        }
    }

    private String locStr(Location l) {
        return ChatColor.AQUA + l.getWorld().getName() + ChatColor.GRAY + " (" +
                String.format("%.1f", l.getX()) + ", " + String.format("%.1f", l.getY()) + ", " + String.format("%.1f", l.getZ()) + ")";
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] a) {
        if (a.length == 1) return java.util.List.of("pos1","pos2","create","remove","list");
        if (a.length == 2 && a[0].equalsIgnoreCase("remove"))
            return manager.listNames().stream().filter(n -> n.startsWith(a[1].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        if (a.length == 3 && a[0].equalsIgnoreCase("create"))
            return Bukkit.getWorlds().stream().map(World::getName).toList();
        return java.util.Collections.emptyList();
    }
}
