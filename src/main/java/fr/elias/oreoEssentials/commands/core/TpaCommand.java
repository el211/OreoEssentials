package fr.elias.oreoEssentials.commands.core;



import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.TeleportService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class TpaCommand implements OreoCommand {
    private final TeleportService tpa;

    public TpaCommand(TeleportService tpa) { this.tpa = tpa; }

    @Override public String name() { return "tpa"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.tpa"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return true; }

    @Override public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) return false;
        Player p = (Player) sender;
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { p.sendMessage("§cPlayer not found."); return true; }
        if (target.equals(p)) { p.sendMessage("§cYou cannot TPA to yourself."); return true; }
        tpa.request(p, target);
        return true;
    }
}

