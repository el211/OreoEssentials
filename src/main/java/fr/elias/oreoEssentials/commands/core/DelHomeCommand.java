package fr.elias.oreoEssentials.commands.core;


import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.HomeService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class DelHomeCommand implements OreoCommand {
    private final HomeService homes;

    public DelHomeCommand(HomeService homes) { this.homes = homes; }

    @Override public String name() { return "delhome"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.delhome"; }
    @Override public String usage() { return "<name>"; }
    @Override public boolean playerOnly() { return true; }

    @Override public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) return false;
        Player p = (Player) sender;
        if (homes.delHome(p.getUniqueId(), args[0])) p.sendMessage("§aHome removed.");
        else p.sendMessage("§cHome not found.");
        return true;
    }
}
