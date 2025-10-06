package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class FlyCommand implements OreoCommand {
    @Override public String name() { return "fly"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.fly"; }
    @Override public String usage() { return "[player]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                Lang.send(sender, "admin.fly.not-found", Map.of("target", args[0]), null);
                return true;
            }
        } else {
            if (!(sender instanceof Player p)) {
                Lang.send(sender, "admin.fly.console-usage", Map.of("label", label), null);
                return true;
            }
            target = p;
        }

        boolean enable = !target.getAllowFlight();
        target.setAllowFlight(enable);

        Lang.send(target, enable ? "admin.fly.enabled" : "admin.fly.disabled", null, target);
        if (target != sender) {
            Lang.send(sender, "admin.fly.toggled-other",
                    Map.of("target", target.getName(), "state", enable ? "enabled" : "disabled"), null);
        }
        return true;
    }
}
