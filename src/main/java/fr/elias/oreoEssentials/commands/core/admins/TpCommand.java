package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class TpCommand implements OreoCommand {

    @Override public String name() { return "tp"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.tp"; }
    @Override public String usage() { return "<player> [target]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0 || args.length > 2) {
            Lang.send(sender, "admin.tp.usage", Map.of("label", label), null);
            return true;
        }

        if (args.length == 1) {
            if (!(sender instanceof Player p)) {
                Lang.send(sender, "admin.tp.console-usage", Map.of("label", label), null);
                return true;
            }
            Player to = Bukkit.getPlayerExact(args[0]);
            if (to == null) {
                Lang.send(p, "admin.tp.not-found", Map.of("target", args[0]), p);
                return true;
            }
            p.teleport(to.getLocation());
            Lang.send(p, "admin.tp.self", Map.of("target", to.getName()), p);
            return true;
        }

        Player from = Bukkit.getPlayerExact(args[0]);
        Player to   = Bukkit.getPlayerExact(args[1]);
        if (from == null || to == null) {
            Lang.send(sender, "admin.tp.not-found-either",
                    Map.of("from", args[0], "to", args[1]), null);
            return true;
        }
        Location dest = to.getLocation();
        from.teleport(dest);

        Lang.send(sender, "admin.tp.other",
                Map.of("from", from.getName(), "to", to.getName()), null);

        if (!from.equals(sender)) {
            Lang.send(from, "admin.tp.notice",
                    Map.of("to", to.getName()), from);
        }
        return true;
    }
}
