package fr.elias.oreoEssentials.commands.completion;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class TpaTabCompleter implements TabCompleter {
    private final OreoEssentials plugin;

    public TpaTabCompleter(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        final String partial = args[0];
        final String want = partial.toLowerCase(Locale.ROOT);

        // Collect from ONLINE players (instant, non-blocking)
        Set<String> out = Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(want))
                .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));

        // Also collect from your Mongo-backed directory (if present)
        var dir = plugin.getPlayerDirectory();
        if (dir != null) {
            // Try a fast suggestNames(prefix, limit) if your directory exposes it
            boolean addedFromDir = false;
            try {
                Method suggest = dir.getClass().getMethod("suggestNames", String.class, int.class);
                @SuppressWarnings("unchecked")
                Collection<String> names = (Collection<String>) suggest.invoke(dir, want, 50);
                if (names != null) {
                    for (String n : names) {
                        if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) out.add(n);
                    }
                    addedFromDir = true;
                }
            } catch (NoSuchMethodException ignored) {
                // fall through to snapshotKnownNames
            } catch (Throwable ignored) { /* keep going with fallbacks */ }

            // Fallback: if your directory has snapshotKnownNames()
            if (!addedFromDir) {
                try {
                    Method snap = dir.getClass().getMethod("snapshotKnownNames");
                    @SuppressWarnings("unchecked")
                    Collection<String> all = (Collection<String>) snap.invoke(dir);
                    if (all != null) {
                        for (String n : all) {
                            if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) out.add(n);
                        }
                    }
                } catch (Throwable ignored) { /* no directory names available */ }
            }
        }

        // Limit length a bit to keep tab menu tidy
        return out.stream().limit(50).toList();
    }
}
