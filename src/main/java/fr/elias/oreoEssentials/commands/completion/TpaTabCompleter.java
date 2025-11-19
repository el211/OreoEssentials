package fr.elias.oreoEssentials.commands.completion;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.playerdirectory.PlayerDirectory;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class TpaTabCompleter implements TabCompleter {
    private final OreoEssentials plugin;

    public TpaTabCompleter(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        String cmdName = cmd.getName().toLowerCase(Locale.ROOT);
        // handle both /tpa and /tp
        if (!(cmdName.equals("tpa") || cmdName.equals("tp"))) {
            return Collections.emptyList();
        }

        if (args.length != 1) return Collections.emptyList();

        final String partial = args[0];
        final String want = partial.toLowerCase(Locale.ROOT);

        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        // 1) local online
        for (Player p : Bukkit.getOnlinePlayers()) {
            String n = p.getName();
            if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) {
                out.add(n);
            }
        }

        // 2) network-wide via PlayerDirectory.suggestOnlineNames()
        var dir = plugin.getPlayerDirectory();
        if (dir != null) {
            try {
                var names = dir.suggestOnlineNames(want, 50);
                if (names != null) {
                    for (String n : names) {
                        if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) {
                            out.add(n);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        return out.stream().limit(50).toList();
    }

}
