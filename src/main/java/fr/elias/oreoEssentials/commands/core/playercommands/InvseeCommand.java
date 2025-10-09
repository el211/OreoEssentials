// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/InvseeCommand.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class InvseeCommand implements OreoCommand, TabCompleter {
    @Override public String name() { return "invsee"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.invsee"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player viewer)) return true;
        if (!viewer.hasPermission("oreo.invsee")) {
            viewer.sendMessage(ChatColor.RED + "You lack permission.");
            return true;
        }
        if (args.length < 1) {
            viewer.sendMessage(ChatColor.RED + "Usage: /invsee <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            viewer.sendMessage(ChatColor.RED + "Player not found or offline.");
            return true;
        }
        viewer.openInventory(target.getInventory());
        viewer.sendMessage(ChatColor.GRAY + "Opened " + ChatColor.AQUA + target.getName() + ChatColor.GRAY + "'s inventory.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(p))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
