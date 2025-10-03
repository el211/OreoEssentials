package fr.elias.oreoEssentials.commands.core.admins;



import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.WarpService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class SetWarpCommand implements OreoCommand {
    private final WarpService warps;

    public SetWarpCommand(WarpService warps) { this.warps = warps; }

    @Override public String name() { return "setwarp"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.setwarp"; }
    @Override public String usage() { return "<name>"; }
    @Override public boolean playerOnly() { return true; }

    @Override public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) return false;
        Player p = (Player) sender;
        warps.setWarp(args[0], p.getLocation());
        p.sendMessage("§aWarp §b" + args[0].toLowerCase() + " §aset.");
        return true;
    }
}

