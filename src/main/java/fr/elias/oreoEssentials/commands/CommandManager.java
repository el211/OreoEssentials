package fr.elias.oreoEssentials.commands;


import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.*;

public class CommandManager {
    private final OreoEssentials plugin;
    private final Map<String, OreoCommand> byName = new HashMap<>();

    public CommandManager(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    public CommandManager register(OreoCommand cmd) {
        byName.put(cmd.name().toLowerCase(), cmd);
        for (String a : cmd.aliases()) byName.put(a.toLowerCase(), cmd);
        // why: ensure executor is our dispatcher even if commands are predeclared
        PluginCommand pc = plugin.getCommand(cmd.name());
        if (pc != null) pc.setExecutor(new Exec());
        for (String a : cmd.aliases()) {
            PluginCommand ac = plugin.getCommand(a);
            if (ac != null) ac.setExecutor(new Exec());
        }
        return this;
    }

    private class Exec implements CommandExecutor {
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            OreoCommand cmd = byName.get(command.getName().toLowerCase());
            if (cmd == null) { sender.sendMessage("§cUnknown command."); return true; }
            if (!cmd.permission().isEmpty() && !sender.hasPermission(cmd.permission())) { sender.sendMessage("§cNo permission."); return true; }
            if (cmd.playerOnly() && !(sender instanceof Player)) { sender.sendMessage("§cPlayers only."); return true; }
            boolean ok = cmd.execute(sender, label, args);
            if (!ok) sender.sendMessage("§eUsage: §7/" + label + " " + cmd.usage());
            return true;
        }
    }
}

