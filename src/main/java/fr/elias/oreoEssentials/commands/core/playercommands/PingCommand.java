package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class PingCommand implements OreoCommand {
    @Override public String name() { return "ping"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.ping"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        int ping = p.getPing(); // 1.21 API
        p.sendMessage(ChatColor.GRAY + "Your ping: " + ChatColor.AQUA + ping + "ms");
        return true;
    }
}
