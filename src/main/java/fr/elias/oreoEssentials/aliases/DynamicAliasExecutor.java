// File: src/main/java/fr/elias/oreoEssentials/aliases/DynamicAliasExecutor.java
package fr.elias.oreoEssentials.aliases;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
        // Resolve definition
        AliasService.AliasDef def = service.get(alias);
        if (def == null || !def.enabled) {
            sender.sendMessage("§cThat alias is disabled.");
            return true;
        }

        // Basic permission gates (customize as needed)
        if (!sender.hasPermission("oreo.alias.use.*")
                && !sender.hasPermission("oreo.alias.use." + alias)) {
            sender.sendMessage("§cYou don’t have permission to use /" + alias + ".");
            return true;
        }

        // Run pre-checks (permissions/money/papi comparisons/etc.)
        if (!service.evaluateAllChecks(sender, def)) {
            String msg = (def.failMessage != null ? def.failMessage : "§cYou don't meet the requirements for /%alias%.")
                    .replace("%alias%", alias);
            try { sender.sendMessage(msg); } catch (Throwable ignored) {}
            return true;
        }

        // Cooldown (touch only after checks pass)
        Player player = (sender instanceof Player p) ? p : null;
        if (!service.checkAndTouchCooldown(alias, player == null ? null : player.getUniqueId(), def.cooldownSeconds)) {
            sender.sendMessage("§cYou must wait before using /" + alias + " again.");
            return true;
        }

        // Execute commands (each entry can contain ';' to chain multiple)
        for (String raw : def.commands) {
            if (raw == null || raw.isEmpty()) continue;
            for (String segment : splitBySemicolon(raw)) {
                String line = segment.trim();
                if (line.isEmpty()) continue;

                // Basic substitutions
                String playerName = player != null ? player.getName() :
                        (sender.getName() != null ? sender.getName() : "CONSOLE");
                line = line.replace("%alias%", alias)
                        .replace("%player%", playerName)
                        .replace("{player}", playerName);

                // UUID / WORLD (empty if not a player)
                String uuid = (player != null) ? player.getUniqueId().toString() : "";
                World w = (player != null) ? player.getWorld() : null;
                String worldName = (w != null) ? w.getName() : "";

                line = line.replace("%uuid%", uuid)
                        .replace("{uuid}", uuid)
                        .replace("%world%", worldName)
                        .replace("{world}", worldName);

                // Args passthroughs
                String allArgs = String.join(" ", args);
                line = line.replace("{args}", allArgs)
                        .replace("%args%", allArgs)
                        .replace("{allargs}", allArgs)
                        .replace("%allargs%", allArgs);

                for (int i = 0; i < args.length; i++) {
                    String a = args[i];
                    line = line.replace("%arg" + (i + 1) + "%", a)
                            .replace("{arg" + (i + 1) + "}", a);
                }

                // PlaceholderAPI (optional)
                if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                    try {
                        line = PlaceholderAPI.setPlaceholders(player, line);
                    } catch (Throwable ignored) {}
                }

                // QoL: shorthand for some give items
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
                    // continue to next chained command
                }
            }
        }

        return true;
    }

    /** Split on ';' while allowing simple quoted segments. */
    private List<String> splitBySemicolon(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false, inDouble = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' && !inDouble) { inSingle = !inSingle; cur.append(c); continue; }
            if (c == '\"' && !inSingle) { inDouble = !inDouble; cur.append(c); continue; }
            if (c == ';' && !inSingle && !inDouble) {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
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
