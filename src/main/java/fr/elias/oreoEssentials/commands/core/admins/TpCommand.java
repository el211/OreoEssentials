// File: src/main/java/fr/elias/oreoEssentials/commands/core/TpCommand.java
package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class TpCommand implements OreoCommand {

    @Override public String name() { return "tp"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.tp"; }
    @Override public String usage() { return "<player> [target]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0 || args.length > 2) return false;

        if (args.length == 1) {
            // /tp <player>  (sender -> player) [player-only]
            if (!(sender instanceof Player p)) {
                sender.sendMessage(ChatColor.RED + "Console: /tp <player> <target>");
                return true;
            }
            Player to = Bukkit.getPlayerExact(args[0]);
            if (to == null) { p.sendMessage(ChatColor.RED + "Player not found."); return true; }
            p.teleport(to.getLocation());
            p.sendMessage(ChatColor.GREEN + "Teleported to " + ChatColor.AQUA + to.getName());
            return true;
        }

        // /tp <player> <target>  (admin: teleport player -> target)
        Player from = Bukkit.getPlayerExact(args[0]);
        Player to   = Bukkit.getPlayerExact(args[1]);
        if (from == null || to == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }
        Location dest = to.getLocation(); // force Location to avoid any ambiguity
        from.teleport(dest);
        sender.sendMessage(ChatColor.GREEN + "Teleported " + ChatColor.AQUA + from.getName()
                + ChatColor.GREEN + " to " + ChatColor.AQUA + to.getName());
        if (!from.equals(sender)) from.sendMessage(ChatColor.YELLOW + "You were teleported to " + to.getName());
        return true;
    }
}
