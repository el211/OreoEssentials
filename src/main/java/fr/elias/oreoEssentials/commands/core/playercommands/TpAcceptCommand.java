package fr.elias.oreoEssentials.commands.core.playercommands;


import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.TeleportService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class TpAcceptCommand implements OreoCommand {
    private final TeleportService tpa;

    public TpAcceptCommand(TeleportService tpa) { this.tpa = tpa; }

    @Override public String name() { return "tpaccept"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.tpa"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override public boolean execute(CommandSender sender, String label, String[] args) {
        return tpa.accept((Player) sender);
    }
}

