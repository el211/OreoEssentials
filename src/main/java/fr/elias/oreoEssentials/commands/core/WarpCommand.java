package fr.elias.oreoEssentials.commands.core;


import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.WarpService;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class WarpCommand implements OreoCommand {
    private final WarpService warps;

    public WarpCommand(WarpService warps) { this.warps = warps; }

    @Override public String name() { return "warp"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.warp"; }
    @Override public String usage() { return "<name>|list"; }
    @Override public boolean playerOnly() { return true; }

    @Override public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            var list = warps.listWarps();
            p.sendMessage("§eWarps: §7" + (list.isEmpty() ? "(none)" :
                    list.stream().collect(Collectors.joining(", "))));
            return true;
        }
        Location l = warps.getWarp(args[0]);
        if (l == null) { p.sendMessage("§cWarp not found."); return true; }
        p.teleport(l);
        p.sendMessage("§aTeleported to warp §b" + args[0].toLowerCase() + "§a.");
        return true;
    }
}

