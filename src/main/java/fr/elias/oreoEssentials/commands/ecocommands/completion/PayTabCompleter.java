package fr.elias.oreoEssentials.commands.ecocommands.completion;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.offline.OfflinePlayerCache;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;
import java.util.stream.Collectors;

public class PayTabCompleter implements TabCompleter {
    private final OreoEssentials plugin;

    public PayTabCompleter(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);

            // Online players on this server
            List<String> suggestions = Bukkit.getOnlinePlayers().stream()
                    .map(p -> p.getName())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(ArrayList::new));

            // Plus cached offline/cross-server names
            OfflinePlayerCache cache = plugin.getOfflinePlayerCache();
            if (cache != null) {
                for (String n : cache.getNames()) {
                    if (n != null && !suggestions.contains(n)) suggestions.add(n);
                }
            }

            return suggestions.stream()
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return List.of("10", "50", "100", "250", "1000").stream()
                    .filter(s -> s.startsWith(args[1]))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
