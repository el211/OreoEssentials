package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class RealNameCommand implements OreoCommand {
    @Override public String name() { return "realname"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.realname"; }
    @Override public String usage() { return "<nickname>"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /realname <nickname>");
            return true;
        }
        String nick = ChatColor.stripColor(args[0]);

        // Try online players by display name first
        for (Player online : Bukkit.getOnlinePlayers()) {
            String disp = ChatColor.stripColor(online.getDisplayName());
            if (disp != null && disp.equalsIgnoreCase(nick)) {
                sender.sendMessage(ChatColor.GOLD + nick + ChatColor.GRAY + " is " + ChatColor.AQUA + online.getName());
                return true;
            }
        }

        // Fallback: try exact match to account name (if user typed account as "nick")
        OfflinePlayer op = Bukkit.getOfflinePlayer(nick);
        if (op != null && op.getName() != null) {
            sender.sendMessage(ChatColor.GOLD + nick + ChatColor.GRAY + " is " + ChatColor.AQUA + op.getName());
            return true;
        }

        sender.sendMessage(ChatColor.RED + "No player found with that nickname.");
        return true;
    }
}
