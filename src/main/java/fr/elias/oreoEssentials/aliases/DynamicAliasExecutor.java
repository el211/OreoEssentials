// File: src/main/java/fr/elias/oreoEssentials/aliases/DynamicAliasExecutor.java
package fr.elias.oreoEssentials.aliases;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.Locale;

final class DynamicAliasExecutor {
    private final Plugin plugin;
    private final AliasService service;
    private final String alias;

    DynamicAliasExecutor(Plugin plugin, AliasService service, String alias) {
        this.plugin = plugin;
        this.service = service;
        this.alias = alias.toLowerCase(Locale.ROOT);
    }

    boolean onAlias(CommandSender sender, String label, String[] args) {
        AliasService.AliasDef def = service.get(alias);
        if (def == null || !def.enabled) {
            sender.sendMessage("§cThat alias is disabled.");
            return true;
        }

        // Basic permission gates (customize as needed)
        if (!sender.hasPermission("oreo.alias.use.*") && !sender.hasPermission("oreo.alias.use." + alias)) {
            sender.sendMessage("§cYou don’t have permission to use /" + alias + ".");
            return true;
        }

        Player player = (sender instanceof Player p) ? p : null;
        if (!service.checkAndTouchCooldown(alias, player == null ? null : player.getUniqueId(), def.cooldownSeconds)) {
            sender.sendMessage("§cYou must wait before using /" + alias + " again.");
            return true;
        }

        // Execute every command line in the alias (split by ';')
        for (String raw : def.commands) {
            String line = raw;

            // Substitute %player% / {player}
            String playerName = player != null ? player.getName() : (sender.getName() != null ? sender.getName() : "CONSOLE");
            line = line.replace("%player%", playerName).replace("{player}", playerName);

            // Simple argument passthrough: {args} or %args%
            if (args.length > 0) {
                String joined = String.join(" ", args);
                line = line.replace("{args}", joined).replace("%args%", joined);
            } else {
                line = line.replace("{args}", "").replace("%args%", "");
            }

            // PlaceholderAPI (optional)
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                try {
                    line = PlaceholderAPI.setPlaceholders(player, line);
                } catch (Throwable ignored) {}
            }

            // QoL: If user typed a vanilla give with shorthand, expand to the namespaced ID
            // e.g. "give %player% iron" -> "give %player% minecraft:iron_ingot"
            // We only touch a minimal set; advise creators to use proper IDs.
            line = maybeExpandShorthandGive(line);

            boolean ok;
            try {
                switch (def.runAs) {
                    case PLAYER -> {
                        if (player == null) {
                            sender.sendMessage("§cThis alias must be run by a player.");
                            return true;
                        }
                        ok = player.performCommand(line);
                    }
                    case CONSOLE -> ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
                    default -> ok = false;
                }
            } catch (Throwable t) {
                ok = false;
                plugin.getLogger().warning("[Aliases] Command failed for /" + alias + ": " + line + " -> " + t.getMessage());
            }

            if (!ok) {
                sender.sendMessage("§cAlias command failed: §7" + line);
                // continue to next line; or break if you want fail-fast
            }
        }

        return true;
    }

    /**
     * Very light helper to reduce “Unknown item 'minecraft:iron'”:
     * If the command begins with "give " and the third token looks like a common shorthand,
     * rewrite to a correct namespaced id.
     */
    private String maybeExpandShorthandGive(String line) {
        String trimmed = line.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith("give ")) return line;

        String[] parts = trimmed.split("\\s+");
        if (parts.length < 3) return line;

        // parts[0] = give, parts[1] = targets, parts[2] = item
        String item = parts[2].toLowerCase(Locale.ROOT);

        // Minimal mapping; encourage creators to use real IDs.
        item = switch (item) {
            case "iron" -> "minecraft:iron_ingot";
            case "gold" -> "minecraft:gold_ingot";
            case "diamond" -> "minecraft:diamond";
            case "emerald" -> "minecraft:emerald";
            default -> item.contains(":") ? item : item; // leave as-is if already namespaced or unknown
        };

        parts[2] = item;
        return String.join(" ", parts);
    }
}
