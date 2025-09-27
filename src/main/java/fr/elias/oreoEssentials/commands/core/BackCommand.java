package fr.elias.oreoEssentials.commands.core;


import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.BackService;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class BackCommand implements OreoCommand {
    private final BackService back;

    public BackCommand(BackService back) { this.back = back; }

    @Override public String name() { return "back"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.back"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;
        Location last = back.getLast(p.getUniqueId());
        if (last == null) { p.sendMessage("§cNo last location recorded."); return true; }
        p.teleport(last);
        p.sendMessage("§aTeleported back.");
        return true;
    }
}
