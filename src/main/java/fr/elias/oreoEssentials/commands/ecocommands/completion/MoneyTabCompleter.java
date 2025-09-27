package fr.elias.oreoEssentials.commands.ecocommands.completion;


import fr.elias.oreoEssentials.OreoEssentials;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;

public class MoneyTabCompleter implements TabCompleter {

    private final OreoEssentials plugin;

    public MoneyTabCompleter(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Only suggest actions if sender has permission
            if (sender.hasPermission("OreoEssentials.money.give")) {
                completions.add("give");
            }
            if (sender.hasPermission("OreoEssentials.money.take")) {
                completions.add("take");
            }
            if (sender.hasPermission("OreoEssentials.money.set")) {
                completions.add("set");
            }
        } else if (args.length == 2) {
            // Suggest all known players (online & offline)
            List<String> names = plugin.getOfflinePlayerCache().getNames();
            Set<String> combinedNames = new HashSet<>(names);

            for (Player player : Bukkit.getOnlinePlayers()) {
                combinedNames.add(player.getName());
            }

            completions.addAll(combinedNames);
            return new ArrayList<>(combinedNames);
        } else if (args.length == 3) {
            // Suggest common money amounts
            completions.add("100");
            completions.add("500");
            completions.add("1000");
        }

        return completions;
    }
}
