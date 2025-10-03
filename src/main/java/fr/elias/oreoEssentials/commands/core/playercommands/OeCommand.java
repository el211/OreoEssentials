package fr.elias.oreoEssentials.commands.core.playercommands;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class OeCommand implements OreoCommand, org.bukkit.command.TabCompleter {

    @Override public String name() { return "oe"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.oe"; }
    @Override public String usage() { return "server <servername>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;

        if (args.length < 2 || !args[0].equalsIgnoreCase("server")) {
            p.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " server <servername>");
            return true;
        }

        String targetServer = args[1];

        // Send player to another server via BungeeCord plugin channel (Velocity supports this too)
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(targetServer);
            p.sendPluginMessage(OreoEssentials.get(), "BungeeCord", out.toByteArray());
            p.sendMessage(ChatColor.GREEN + "Connecting you to " + ChatColor.AQUA + targetServer + ChatColor.GREEN + "…");
        } catch (Throwable t) {
            p.sendMessage(ChatColor.RED + "Could not connect you to " + targetServer + ". Is the proxy/channel configured?");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission(permission())) return Collections.emptyList();
        if (args.length == 1) {
            return List.of("server");
        }
        // (Optional) you could implement GetServers round-trip caching here for tab completion.
        return Collections.emptyList();
    }
}
