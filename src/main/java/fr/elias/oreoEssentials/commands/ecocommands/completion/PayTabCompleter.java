package fr.elias.oreoEssentials.commands.ecocommands.completion;


import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class PayTabCompleter implements TabCompleter {

    private final OreoEssentials plugin;

    public PayTabCompleter(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return suggestions;
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            String input = args[0].toLowerCase();

            // ✅ Combine online and cached offline names
            Set<String> allNames = new HashSet<>(plugin.getOfflinePlayerCache().getNames());
            for (Player online : Bukkit.getOnlinePlayers()) {
                allNames.add(online.getName());
            }

            // ✅ Filter suggestions
            return allNames.stream()
                    .filter(name -> !name.equalsIgnoreCase(player.getName()))
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .sorted()
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return Arrays.asList("10", "50", "100", "500", "1000");
        }

        return suggestions;
    }
}

