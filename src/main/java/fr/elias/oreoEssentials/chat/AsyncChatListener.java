// File: src/main/java/fr/elias/oreoEssentials/chat/AsyncChatListener.java
package fr.elias.oreoEssentials.chat;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.services.chatservices.MuteService;
import fr.elias.oreoEssentials.util.ChatSyncManager;
import fr.elias.oreoEssentials.util.DiscordWebhook;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Formats chat using FormatManager, optionally syncs to RabbitMQ,
 * and optionally forwards to Discord via webhook if enabled in config.
 */
public class AsyncChatListener implements Listener {

    private final FormatManager formatManager;
    private final CustomConfig chatConfig;
    private final ChatSyncManager syncManager;
    private final boolean discordEnabled;
    private final String discordWebhookUrl;
    private final MuteService muteService; // used to prevent publishing when muted

    public AsyncChatListener(
            FormatManager fm,
            CustomConfig cfg,
            ChatSyncManager sync,
            boolean discordEnabled,
            String discordWebhookUrl,
            MuteService muteService
    ) {
        this.formatManager = fm;
        this.chatConfig = cfg;
        this.syncManager = sync;
        this.discordEnabled = discordEnabled;
        this.discordWebhookUrl = (discordWebhookUrl == null) ? "" : discordWebhookUrl.trim();
        this.muteService = muteService;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        // If something else (e.g., MuteListener) cancelled already, we won't run (ignoreCancelled=true)

        // Respect chat.enabled in chat-format.yml
        final FileConfiguration conf = chatConfig == null ? null : chatConfig.getCustomConfig();
        if (conf == null || !conf.getBoolean("chat.enabled", false)) {
            // Let vanilla/bukkit handle chat if plugin chat is disabled
            return;
        }

        final Player player = event.getPlayer();

        // Extra guard: if muted, do not format/relay/broadcast here either
        if (muteService != null && muteService.isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // We take over the chat pipeline
        event.setCancelled(true);

        String message = event.getMessage();
        if (message == null) message = "";
        message = message.trim();
        if (message.isEmpty()) return; // ignore empty messages

        // Format (plugin-defined) -> may include color codes (&)
        String formatted = formatManager.formatMessage(player, message);

        // PlaceholderAPI expansion (best-effort)
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                formatted = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, formatted);
            }
        } catch (Throwable ignored) {
            // Keep formatted as-is on any PAPI failure
        }

        // Translate & broadcast to Minecraft players on main thread
        final String mcOut = ChatColor.translateAlternateColorCodes('&', formatted);
        Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> Bukkit.broadcastMessage(mcOut));

        // Optional cross-server sync (RabbitMQ) — include sender UUID
        try {
            if (syncManager != null) {
                // Requires ChatSyncManager.publishMessage(UUID playerId, String serverName, String playerName, String message)
                syncManager.publishMessage(
                        player.getUniqueId(),
                        Bukkit.getServer().getName(),
                        player.getName(),
                        stripColors(formatted)
                );
            }
        } catch (Throwable ex) {
            Bukkit.getLogger().severe("[OreoEssentials] ChatSync publish failed: " + ex.getMessage());
        }

        // Optional Discord webhook (guarded by flag + URL)
        maybeSendToDiscord(player.getName(), stripColors(formatted));
    }

    /** Send to Discord only if enabled and URL is present. */
    private void maybeSendToDiscord(String username, String content) {
        if (!discordEnabled) return;
        if (discordWebhookUrl.isEmpty()) return;
        try {
            DiscordWebhook webhook = new DiscordWebhook(OreoEssentials.get(), discordWebhookUrl);
            webhook.sendAsync(username, content);
        } catch (Throwable ex) {
            Bukkit.getLogger().warning("[OreoEssentials] Discord webhook send failed: " + ex.getMessage());
        }
    }

    /** Basic color/formatting strip for cleaner external outputs (sync/discord). */
    private String stripColors(String s) {
        if (s == null) return "";
        // Remove legacy ampersand codes first, then strip Bukkit color section (§) if present
        String noAmp = s.replaceAll("(?i)&[0-9A-FK-OR]", "");
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', noAmp));
    }
}
