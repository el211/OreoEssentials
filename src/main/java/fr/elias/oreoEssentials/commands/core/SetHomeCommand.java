// File: src/main/java/fr/elias/oreoEssentials/commands/core/SetHomeCommand.java
package fr.elias.oreoEssentials.commands.core;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.ConfigService;
import fr.elias.oreoEssentials.services.HomeService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class SetHomeCommand implements OreoCommand {
    private final HomeService homes;
    private final ConfigService config;

    public SetHomeCommand(HomeService homes, ConfigService config) {
        this.homes = homes;
        this.config = config;
    }

    @Override public String name() { return "sethome"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.sethome"; }
    @Override public String usage() { return "<name>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) return false;
        Player p = (Player) sender;
        boolean ok = homes.setHome(p, args[0], p.getLocation());
        if (!ok) {
            int max = config.getMaxHomesFor(p);
            p.sendMessage("§cHome limit reached (" + max + "). Ask staff for a higher rank.");
            return true;
        }
        p.sendMessage("§aHome §b" + args[0].toLowerCase() + " §aset.");
        return true;
    }
}
