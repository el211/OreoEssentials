// src/main/java/fr/elias/oreoEssentials/commands/core/FreezeCommand.java
package fr.elias.oreoEssentials.commands.core.moderation;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.FreezeService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class FreezeCommand implements OreoCommand {
    private final FreezeService service;
    public FreezeCommand(FreezeService service) { this.service = service; }

    @Override public String name() { return "freeze"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.freeze"; }
    @Override public String usage() { return "<player> [on|off]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1 || args.length > 2) return false;
        Player p = Bukkit.getPlayerExact(args[0]);
        if (p == null) { sender.sendMessage(ChatColor.RED + "Player not found."); return true; }

        boolean state = !service.isFrozen(p.getUniqueId());
        if (args.length == 2) {
            if (args[1].equalsIgnoreCase("on")) state = true;
            else if (args[1].equalsIgnoreCase("off")) state = false;
            else return false;
        }

        service.set(p.getUniqueId(), state);
        if (state) {
            p.sendMessage(ChatColor.RED + "You have been frozen by staff.");
            sender.sendMessage(ChatColor.GREEN + "Froze " + ChatColor.AQUA + p.getName());
        } else {
            p.sendMessage(ChatColor.YELLOW + "You have been unfrozen.");
            sender.sendMessage(ChatColor.GREEN + "Unfroze " + ChatColor.AQUA + p.getName());
        }
        return true;
    }
}
