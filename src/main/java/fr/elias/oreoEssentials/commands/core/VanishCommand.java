// File: src/main/java/fr/elias/oreoEssentials/commands/core/VanishCommand.java
package fr.elias.oreoEssentials.commands.core;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.VanishService;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class VanishCommand implements OreoCommand {

    private final VanishService service;

    public VanishCommand(VanishService service) { this.service = service; }

    @Override public String name() { return "vanish"; }
    @Override public List<String> aliases() { return List.of("v"); }
    @Override public String permission() { return "oreo.vanish"; }
    @Override public String usage() { return "[on|off]"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;
        boolean target = service.isVanished(p.getUniqueId());

        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("on"))  target = true;
            else if (args[0].equalsIgnoreCase("off")) target = false;
            else return false;
        } else if (args.length > 1) {
            return false;
        } else {
            target = !target; // toggle
        }

        service.setVanished(OreoEssentials.get(), p, target);
        p.sendMessage(target
                ? ChatColor.GREEN + "You are now vanished."
                : ChatColor.YELLOW + "You are now visible.");
        return true;
    }
}
