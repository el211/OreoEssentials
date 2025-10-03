package fr.elias.oreoEssentials.commands.core.moderation;

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
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;
        boolean nowVanished = service.toggle(p);
        p.sendMessage(nowVanished
                ? ChatColor.GREEN + "You are now vanished."
                : ChatColor.YELLOW + "You are now visible.");
        return true;
    }
}
