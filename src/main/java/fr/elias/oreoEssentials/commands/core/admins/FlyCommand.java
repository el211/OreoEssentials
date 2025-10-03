package fr.elias.oreoEssentials.commands.core.admins;


import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class FlyCommand implements OreoCommand {
    @Override public String name() { return "fly"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.fly"; }
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
        boolean enable = !target.getAllowFlight();
        target.setAllowFlight(enable);
        target.sendMessage(enable ? "§aFlight enabled." : "§cFlight disabled.");
        if (target != sender) sender.sendMessage("§eToggled flight for §b" + target.getName());
        return true;
    }
}

