package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class TphereCommand implements OreoCommand, org.bukkit.command.TabCompleter {

    @Override public String name() { return "tphere"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.tphere"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player self = (Player) sender;

        if (args.length < 1) {
            Lang.send(self, "admin.tphere.usage", java.util.Map.of("label", label), self);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Lang.send(self, "admin.tphere.not-found", java.util.Map.of("target", args[0]), self);
            return true;
        }

        target.teleport(self.getLocation());
        Lang.send(self, "admin.tphere.brought", java.util.Map.of("target", target.getName()), self);
        if (!target.equals(self)) {
            Lang.send(target, "admin.tphere.notice", java.util.Map.of("player", self.getName()), target);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
