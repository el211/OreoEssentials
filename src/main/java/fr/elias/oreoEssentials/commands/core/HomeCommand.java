// File: src/main/java/fr/elias/oreoEssentials/commands/core/HomeCommand.java
package fr.elias.oreoEssentials.commands.core;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.HomeService;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class HomeCommand implements OreoCommand {
    private final HomeService homes;

    public HomeCommand(HomeService homes) { this.homes = homes; }

    @Override public String name() { return "home"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.home"; }
    @Override public String usage() { return "<name>|list"; }
    @Override public boolean playerOnly() { return true; }

    @Override public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            var set = homes.homes(p.getUniqueId());
            p.sendMessage("§eHomes: §7" + (set.isEmpty() ? "(none)" :
                    set.stream().collect(Collectors.joining(", "))));
            return true;
        }
        Location l = homes.getHome(p.getUniqueId(), args[0]);
        if (l == null) { p.sendMessage("§cHome not found."); return true; }
        p.teleport(l);
        p.sendMessage("§aTeleported to home §b" + args[0].toLowerCase() + "§a.");
        return true;
    }
}
