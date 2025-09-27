package fr.elias.oreoEssentials.commands.core;


import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.SpawnService;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class SpawnCommand implements OreoCommand {
    private final SpawnService spawn;

    public SpawnCommand(SpawnService spawn) { this.spawn = spawn; }

    @Override public String name() { return "spawn"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.spawn"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;
        Location l = spawn.getSpawn();
        if (l == null) { p.sendMessage("§cSpawn is not set."); return true; }
        p.teleport(l);
        p.sendMessage("§aTeleported to spawn.");
        return true;
    }
}

