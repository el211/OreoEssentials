// src/main/java/fr/elias/oreoEssentials/commands/core/KickCommand.java
package fr.elias.oreoEssentials.commands.core;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class KickCommand implements OreoCommand {
    @Override public String name() { return "kick"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.kick"; }
    @Override public String usage() { return "<player> [reason...]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) return false;
        Player p = Bukkit.getPlayerExact(args[0]);
        if (p == null) { sender.sendMessage(ChatColor.RED + "Player not found."); return true; }
        String reason = args.length >= 2 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Kicked by an operator.";
        p.kickPlayer(ChatColor.RED + reason);
        sender.sendMessage(ChatColor.GREEN + "Kicked " + ChatColor.AQUA + p.getName() + ChatColor.GREEN + ". Reason: " + ChatColor.YELLOW + reason);
        return true;
    }
}
