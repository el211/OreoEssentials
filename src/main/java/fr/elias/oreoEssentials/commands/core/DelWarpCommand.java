package fr.elias.oreoEssentials.commands.core;



import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.WarpService;
import org.bukkit.command.CommandSender;

import java.util.List;

public class DelWarpCommand implements OreoCommand {
    private final WarpService warps;

    public DelWarpCommand(WarpService warps) { this.warps = warps; }

    @Override public String name() { return "delwarp"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.delwarp"; }
    @Override public String usage() { return "<name>"; }
    @Override public boolean playerOnly() { return false; }

    @Override public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) return false;
        if (warps.delWarp(args[0])) sender.sendMessage("§aWarp removed.");
        else sender.sendMessage("§cWarp not found.");
        return true;
    }
}

