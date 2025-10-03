package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.DeathBackService;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class DeathBackCommand implements OreoCommand {
    private final DeathBackService deathBack;

    public DeathBackCommand(DeathBackService deathBack) {
        this.deathBack = deathBack;
    }

    @Override public String name() { return "deathback"; }
    @Override public List<String> aliases() { return List.of("backdeath","db"); }
    @Override public String permission() { return "oreo.deathback"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;
        Location loc = deathBack.getLastDeath(p.getUniqueId());
        if (loc == null) {
            p.sendMessage(ChatColor.RED + "No death location stored.");
            return true;
        }
        boolean ok = p.teleport(loc);
        if (ok) {
            p.sendMessage(ChatColor.GREEN + "Teleported to your last death.");
            // Optional: clear after use
            // deathBack.clear(p.getUniqueId());
        } else {
            p.sendMessage(ChatColor.RED + "Teleport failed.");
        }
        return true;
    }
}
