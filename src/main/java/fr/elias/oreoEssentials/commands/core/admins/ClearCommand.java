package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ClearCommand implements OreoCommand {
    @Override public String name() { return "clear"; }
    @Override public List<String> aliases() { return List.of("ci"); }
    @Override public String permission() { return "oreo.clear"; }
    @Override public String usage() { return "[player]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(ChatColor.RED + "Console must specify a player: /clear <player>");
                return true;
            }
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            p.getInventory().setItemInOffHand(null);
            p.sendMessage(ChatColor.GREEN + "Inventory cleared.");
            return true;
        }

        if (!sender.hasPermission("oreo.clear.others")) {
            sender.sendMessage(ChatColor.RED + "You lack permission: oreo.clear.others");
            return true;
        }
        Player t = Bukkit.getPlayerExact(args[0]);
        if (t == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }
        t.getInventory().clear();
        t.getInventory().setArmorContents(null);
        t.getInventory().setItemInOffHand(null);
        t.sendMessage(ChatColor.YELLOW + "Your inventory was cleared by " + sender.getName() + ".");
        sender.sendMessage(ChatColor.GREEN + "Cleared " + t.getName() + "'s inventory.");
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        // /clear <player>
        if (args.length == 1) {
            if (!sender.hasPermission("oreo.clear.others")) {
                return List.of();
            }

            String prefix = args[0].toLowerCase(Locale.ROOT);

            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}
