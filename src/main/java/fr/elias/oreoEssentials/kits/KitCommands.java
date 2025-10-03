// File: src/main/java/fr/elias/oreoEssentials/kits/KitCommands.java
package fr.elias.oreoEssentials.kits;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
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
            if (!(sender instanceof Player p)) { sender.sendMessage("Only players."); return true; }
            if (!p.hasPermission("oreo.kits.open")) { p.sendMessage("§cNo permission."); return true; }
            // Open SmartInvs menu
            KitsMenuSI.open(plugin, manager, p);
            return true;
        }

        if (cmd.equals("kit")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Only players."); return true; }
            if (args.length < 1) { p.sendMessage("§eUsage: /kit <name>"); return true; }
            boolean handled = manager.claim(p, args[0]);
            if (!handled) p.sendMessage("§cUnknown kit: §e" + args[0]);
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("kit")) return List.of();
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
}
