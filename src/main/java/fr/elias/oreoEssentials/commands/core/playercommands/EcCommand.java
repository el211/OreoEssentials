// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/EcCommand.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.enderchest.EnderChestService;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class EcCommand implements OreoCommand {

    private final EnderChestService ecService;
    private final boolean crossServer;

    public EcCommand(EnderChestService ecService, boolean crossServer) {
        this.ecService = ecService;
        this.crossServer = crossServer;
    }

    @Override public String name() { return "ec"; }
    @Override public List<String> aliases() { return List.of("enderchest"); }
    @Override public String permission() { return "oreo.ec"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        p.openInventory(ecService.createVirtualEc(p));
        if (!crossServer) {
            p.sendMessage(ChatColor.YELLOW + "Note: cross-server EC is disabled. This EC is local to this server.");
        }
        return true;
    }
}
