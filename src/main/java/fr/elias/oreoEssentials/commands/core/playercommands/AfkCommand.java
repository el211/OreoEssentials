package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.AfkService;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class AfkCommand implements OreoCommand {
    private final AfkService afk;

    public AfkCommand(AfkService afk) { this.afk = afk; }

    @Override public String name() { return "afk"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.afk"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        boolean nowAfk = afk.toggleAfk(p);
        if (nowAfk) {
            p.sendMessage(ChatColor.YELLOW + "You are now AFK.");
        } else {
            p.sendMessage(ChatColor.GREEN + "You are no longer AFK.");
        }
        return true;
    }
}
