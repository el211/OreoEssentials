// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/KillCommand.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class KillCommand implements OreoCommand {
    @Override public String name() { return "kill"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.kill"; }
    @Override public String usage() { return "[player]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(ChatColor.RED + "Usage: /kill <player>");
                return true;
            }
            // kill self
            if (!p.hasPermission("oreo.kill")) {
                p.sendMessage(ChatColor.RED + "You lack permission.");
                return true;
            }
            p.setHealth(0.0);
            p.sendMessage(ChatColor.RED + "You have been slain.");
            return true;
        }

        // kill others
        if (!(sender.hasPermission("oreo.kill.others"))) {
            sender.sendMessage(ChatColor.RED + "You lack permission to kill others.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }
        target.setHealth(0.0);
        sender.sendMessage(ChatColor.GREEN + "Killed " + ChatColor.AQUA + target.getName() + ChatColor.GREEN + ".");
        return true;
    }
}
