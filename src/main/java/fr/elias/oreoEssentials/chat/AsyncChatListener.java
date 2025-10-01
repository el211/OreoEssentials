package fr.elias.oreoEssentials.chat;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.ChatSyncManager;
import fr.elias.oreoEssentials.util.DiscordWebhook;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Formats chat using FormatManager, optionally syncs to RabbitMQ,
 * and optionally forwards to Discord via webhook if enabled in config.
 */
public class AsyncChatListener implements Listener {
    private final FormatManager formatManager;
    private final CustomConfig chatConfig;          // wrapper around chat-format.yml
    private final ChatSyncManager syncManager;      // may be disabled/NO-OP internally
    private final boolean discordEnabled;
    private final String discordWebhookUrl;

    public AsyncChatListener(FormatManager fm,
                             CustomConfig cfg,
                             ChatSyncManager sync,
                             boolean discordEnabled,
                             String discordWebhookUrl) {
        this.formatManager = fm;
        this.chatConfig = cfg;
        this.syncManager = sync;
        this.discordEnabled = discordEnabled;
        this.discordWebhookUrl = (discordWebhookUrl == null) ? "" : discordWebhookUrl.trim();
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        // Respect chat.enabled in chat-format.yml
        FileConfiguration conf = chatConfig.getCustomConfig();
        if (conf == null || !conf.getBoolean("chat.enabled", false)) {
            return; // Let vanilla/bukkit handle chat if plugin chat is disabled
        }

        // We take over the chat pipeline
        event.setCancelled(true);

        Player player = event.getPlayer();
        String message = event.getMessage();

        // Format with your FormatManager (supports default/vip paths etc.)
        String formatted = formatManager.formatMessage(player, message);

        // PlaceholderAPI expansion (safe guard)
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                formatted = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, formatted);
            }
        } catch (Throwable ignored) {
            // do not fail chat if PAPI errors
        }

        // Translate & broadcast to Minecraft players on main thread
        final String mcOut = ChatColor.translateAlternateColorCodes('&', formatted);
        Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> Bukkit.broadcastMessage(mcOut));

        // Optional cross-server sync (RabbitMQ) — best effort
        try {
            if (syncManager != null) {
                syncManager.publishMessage(Bukkit.getServer().getName(), player.getName(), stripColors(formatted));
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
            // Use your helper’s (Plugin, String) constructor + sendAsync
            DiscordWebhook webhook = new DiscordWebhook(OreoEssentials.get(), discordWebhookUrl);
            webhook.sendAsync(username, content);
        } catch (Throwable ex) {
            Bukkit.getLogger().warning("[OreoEssentials] Discord webhook send failed: " + ex.getMessage());
        }
    }

    /** Basic color/formatting strip for cleaner external outputs (sync/discord). */
    private String stripColors(String s) {
        if (s == null) return "";
        // Remove Minecraft Section codes (&a, &l...) and § codes just in case
        String noAmp = s.replaceAll("(?i)&[0-9A-FK-OR]", "");
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', noAmp));
    }
}
