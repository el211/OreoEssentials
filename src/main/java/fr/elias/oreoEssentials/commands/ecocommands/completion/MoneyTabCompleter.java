package fr.elias.oreoEssentials.commands.ecocommands.completion;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.playerdirectory.PlayerDirectory;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class MoneyTabCompleter implements TabCompleter {

    private final OreoEssentials plugin;

    public MoneyTabCompleter(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // /money <action> [player] [amount]

        // -------- arg 1: action (give/take/set) ----------
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();

            if (sender.hasPermission("OreoEssentials.money.give")) {
                completions.add("give");
            }
            if (sender.hasPermission("OreoEssentials.money.take")) {
                completions.add("take");
            }
            if (sender.hasPermission("OreoEssentials.money.set")) {
                completions.add("set");
            }

            String prefix = args[0].toLowerCase(Locale.ROOT);
            return completions.stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        // -------- arg 2: player name ----------
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            Set<String> names = new HashSet<>();

            // 1) Local online players
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName() != null) {
                    names.add(p.getName());
                }
            }

            // 2) Network-online players via PlayerDirectory
            PlayerDirectory dir = plugin.getPlayerDirectory(); // adjust getter name if needed
            if (dir != null) {
                names.addAll(dir.suggestOnlineNames(prefix, 80));
            }

            return names.stream()
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        // -------- arg 3: amount ----------
        if (args.length == 3) {
            List<String> amounts = List.of("100", "500", "1000", "5000", "10000");
            String prefix = args[2];
            return amounts.stream()
                    .filter(a -> a.startsWith(prefix))
                    .toList();
        }

        return List.of();
    }
}
