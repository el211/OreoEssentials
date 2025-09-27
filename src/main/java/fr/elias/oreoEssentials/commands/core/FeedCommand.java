package fr.elias.oreoEssentials.commands.core;


import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class FeedCommand implements OreoCommand {
    @Override public String name() { return "feed"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.feed"; }
    @Override public String usage() { return "[player]"; }
    @Override public boolean playerOnly() { return false; }

    @Override public boolean execute(CommandSender sender, String label, String[] args) {
        Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage("§cPlayer not found."); return true; }
        } else {
            if (!(sender instanceof Player)) return false;
            target = (Player) sender;
        }
        target.setFoodLevel(20);
        target.setSaturation(20f);
        target.sendMessage("§aFed.");
        if (target != sender) sender.sendMessage("§eFed §b" + target.getName());
        return true;
    }
}
