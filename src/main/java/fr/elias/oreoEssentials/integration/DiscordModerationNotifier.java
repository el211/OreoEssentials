// File: src/main/java/fr/elias/oreoEssentials/integration/DiscordModerationNotifier.java
package fr.elias.oreoEssentials.integration;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.chat.CustomConfig;
import fr.elias.oreoEssentials.util.DiscordWebhook;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DiscordModerationNotifier {

    public enum EventType { KICK, BAN, UNBAN, MUTE, UNMUTE }

    private final OreoEssentials plugin;
    private final CustomConfig cfg; // wraps discord-integration.yml
    private boolean enabled;
    private String defaultWebhook;
    private String defaultUsername;
    private boolean includeServerName;

    public DiscordModerationNotifier(OreoEssentials plugin) {
        this.plugin = plugin;
        this.cfg = new CustomConfig(plugin, "discord-integration.yml");
        reload();
    }

    public void reload() {
        var c = cfg.getCustomConfig();
        this.enabled            = c.getBoolean("enabled", false);
        this.defaultWebhook     = safe(c.getString("default_webhook", ""));
        this.defaultUsername    = safe(c.getString("default_username", "Oreo Moderation"));
        this.includeServerName  = c.getBoolean("include_server_name", true);
    }

    public boolean isEnabled() {
        return enabled && hasAnyWebhook();
    }

    /* ------------------ Public: call these from commands ------------------ */

    public void notifyKick(String targetName, UUID targetId, String reason, String by) {
        Map<String, String> ctx = baseCtx("KICK", targetName, targetId, by, reason);
        send(EventType.KICK, ctx);
    }

    public void notifyBan(String targetName, UUID targetId, String reason, String by, Long untilEpochMillis) {
        Map<String, String> ctx = baseCtx("BAN", targetName, targetId, by, reason);
        addTime(ctx, untilEpochMillis);
        send(EventType.BAN, ctx);
    }

    public void notifyUnban(String targetName, UUID targetId, String by) {
        Map<String, String> ctx = baseCtx("UNBAN", targetName, targetId, by, null);
        send(EventType.UNBAN, ctx);
    }

    public void notifyMute(String targetName, UUID targetId, String reason, String by, long untilEpochMillis) {
        Map<String, String> ctx = baseCtx("MUTE", targetName, targetId, by, reason);
        addTime(ctx, (untilEpochMillis <= 0) ? null : untilEpochMillis);
        send(EventType.MUTE, ctx);
    }

    public void notifyUnmute(String targetName, UUID targetId, String by) {
        Map<String, String> ctx = baseCtx("UNMUTE", targetName, targetId, by, null);
        send(EventType.UNMUTE, ctx);
    }

    /* ------------------ Core sending ------------------ */

    private void send(EventType type, Map<String, String> ctx) {
        if (!enabled) return;

        var c = cfg.getCustomConfig();
        String node = "events." + type.name().toLowerCase();

        // Event toggle (default true)
        if (!c.getBoolean(node + ".enabled", true)) return;

        String webhook  = safe(c.getString(node + ".webhook", defaultWebhook));
        String username = safe(c.getString(node + ".username", defaultUsername));
        String template = safe(c.getString(node + ".message",
                "**[{action}]** **Player:** {player} (`{uuid}`){by? | **By:** {by}}{reason? | **Reason:** {reason}}{time_abs? | **Until:** {time_abs}}"));

        if (webhook.isEmpty()) return;

        // Add derived placeholders
        ctx.put("server", Bukkit.getServer().getName());
        ctx.put("action", type.name());

        // Optional server prefix if enabled (you can also add {server} in your own template)
        String content = renderTemplate(template, ctx, includeServerName);

        try {
            DiscordWebhook wh = new DiscordWebhook(plugin, webhook);
            wh.sendAsync(username, content);
        } catch (Throwable t) {
            plugin.getLogger().warning("[DiscordModerationNotifier] Failed to send " + type + " webhook: " + t.getMessage());
        }
    }

    /* ------------------ Helpers ------------------ */

    private Map<String, String> baseCtx(String action,
                                        String targetName,
                                        UUID targetId,
                                        String by,
                                        String reason) {
        Map<String, String> m = new HashMap<>();
        m.put("action",  safe(action));
        m.put("player",  safe(targetName));
        m.put("uuid",    targetId == null ? "" : targetId.toString());
        m.put("by",      safe(by));
        m.put("reason",  safe(reason));
        return m;
    }

    /** Adds time placeholders (absolute/relative) or marks Permanent if null. */
    private void addTime(Map<String, String> ctx, Long untilEpochMillis) {
        if (untilEpochMillis == null) {
            ctx.put("time_abs", "Permanent");
            ctx.put("time_rel", "Permanent");
        } else {
            long seconds = Math.max(0, untilEpochMillis / 1000L);
            // Discord timestamp formatting
            ctx.put("time_abs", "<t:" + seconds + ":F>");
            ctx.put("time_rel", "<t:" + seconds + ":R>");
        }
    }

    /**
     * Simple template renderer with:
     *  - plain placeholders: {player}, {uuid}, {reason}, {by}, {time_abs}, {time_rel}, {server}, {action}
     *  - conditional fragments: {reason? | some text with {reason}} -> included only if {reason} is non-empty
     *
     * If includeServerName=true, we’ll prefix with "**(server)** " unless the template already uses {server}.
     */
    private String renderTemplate(String template, Map<String, String> ctx, boolean addServerPrefix) {
        String out = template;

        // Handle conditionals first: {key? | text}
        // This is a very small parser: finds occurrences and decides to keep/strip
        int idx;
        while ((idx = out.indexOf("{")) != -1) {
            int end = out.indexOf("}", idx);
            if (end == -1) break;
            String token = out.substring(idx + 1, end).trim();

            if (token.contains("?")) {
                // format: key? | text...
                String[] parts = token.split("\\?", 2);
                String key = parts[0].trim();
                String remainder = parts[1].trim();
                // remainder should start with | ...
                String text = remainder.startsWith("|") ? remainder.substring(1).trim() : remainder;

                boolean present = ctx.containsKey(key) && !safe(ctx.get(key)).isEmpty();
                String replacement = present ? replacePlaceholders(text, ctx) : "";
                out = out.substring(0, idx) + replacement + out.substring(end + 1);
            } else {
                // not a conditional; just skip now, we’ll replace later
                out = out.substring(0, idx) + "{" + token + "}" + out.substring(end + 1);
                // move after this } to avoid infinite loop
                int nextStart = out.indexOf("}", idx);
                if (nextStart == -1) break;
            }
        }

        // Replace plain placeholders
        out = replacePlaceholders(out, ctx);

        // Optional server prefix if user didn’t include {server} themselves
        if (addServerPrefix && !template.contains("{server}")) {
            out = "**(" + ctx.getOrDefault("server", "Server") + ")** " + out;
        }

        return out;
    }

    private String replacePlaceholders(String s, Map<String, String> ctx) {
        String out = s;
        for (var e : ctx.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", safe(e.getValue()));
        }
        return out;
    }

    private boolean hasAnyWebhook() {
        var c = cfg.getCustomConfig();
        if (!safe(c.getString("default_webhook", "")).isEmpty()) return true;
        for (EventType t : EventType.values()) {
            String node = "events." + t.name().toLowerCase() + ".webhook";
            if (!safe(c.getString(node, "")).isEmpty()) return true;
        }
        return false;
    }

    private static String safe(String s) { return (s == null) ? "" : s.trim(); }
}
