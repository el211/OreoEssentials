package fr.elias.oreoEssentials.kits;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class KitCommands implements CommandExecutor, TabCompleter {
    private final OreoEssentials plugin;
    private final KitsManager manager;

    public KitCommands(OreoEssentials plugin, KitsManager manager) {
        this.plugin = plugin;
        this.manager = manager;

        if (plugin.getCommand("kits") != null) {
            plugin.getCommand("kits").setExecutor(this);
            plugin.getCommand("kits").setTabCompleter(this);
        }
        if (plugin.getCommand("kit") != null) {
            plugin.getCommand("kit").setExecutor(this);
            plugin.getCommand("kit").setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("kits")) {
            // /kits toggle
            if (args.length > 0 && args[0].equalsIgnoreCase("toggle")) {
                if (!sender.hasPermission("oreo.kits.admin")) {
                    sender.sendMessage("§cNo permission.");
                    return true;
                }
                boolean now = manager.toggleEnabled();
                sender.sendMessage("§7Kits feature is now " + (now ? "§aENABLED" : "§cDISABLED"));
                return true;
            }

            if (!(sender instanceof Player p)) { sender.sendMessage("Only players."); return true; }

            if (!p.hasPermission("oreo.kits.open")) { p.sendMessage("§cNo permission."); return true; }

            if (!manager.isEnabled()) {
                p.sendMessage("§cKits are currently disabled.");
                if (p.hasPermission("oreo.kits.admin")) {
                    p.sendMessage("§7Use §f/kits toggle §7to enable it.");
                }
                return true;
            }

            // Open SmartInvs menu
            KitsMenuSI.open(plugin, manager, p);
            return true;
        }

        if (cmd.equals("kit")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Only players."); return true; }
            if (args.length < 1) { p.sendMessage("§eUsage: /kit <name>"); return true; }

            if (!manager.isEnabled()) {
                p.sendMessage("§cKits are currently disabled.");
                if (p.hasPermission("oreo.kits.admin")) {
                    p.sendMessage("§7Use §f/kits toggle §7to enable it.");
                }
                return true;
            }

            boolean handled = manager.claim(p, args[0]);
            if (!handled) p.sendMessage("§cUnknown kit: §e" + args[0]);
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);

        if (name.equals("kits")) {
            if (args.length == 1) {
                List<String> out = new ArrayList<>();
                out.add("toggle");
                return out;
            }
            return List.of();
        }

        if (name.equals("kit")) {
            if (args.length == 1) {
                List<String> out = new ArrayList<>();
                String start = args[0].toLowerCase(Locale.ROOT);
                for (String id : manager.getKits().keySet()) {
                    if (id.startsWith(start)) out.add(id);
                }
                return out;
            }
            return List.of();
        }

        return List.of();
    }
}
