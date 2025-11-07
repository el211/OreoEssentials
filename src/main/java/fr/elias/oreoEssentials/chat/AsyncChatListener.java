package fr.elias.oreoEssentials.chat;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.services.chatservices.MuteService;
import fr.elias.oreoEssentials.util.ChatSyncManager;
import fr.elias.oreoEssentials.util.DiscordWebhook;
import fr.elias.oreoEssentials.chat.channel.ChannelManager;
import fr.elias.oreoEssentials.chat.channel.ChatChannel;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Formats chat using FormatManager, routes via ChannelManager,
 * optionally syncs to RabbitMQ (per-channel), and optionally forwards
 * GLOBAL to Discord via webhook if enabled in config.
 */
public class AsyncChatListener implements Listener {

    private final FormatManager formatManager;
    private final CustomConfig chatConfig;
    private final ChatSyncManager syncManager;
    private final boolean discordEnabled;
    private final String discordWebhookUrl;
    private final MuteService muteService; // used to prevent publishing when muted

    public AsyncChatListener(FormatManager fm,
                             CustomConfig cfg,
                             ChatSyncManager sync,
                             boolean discordEnabled,
                             String discordWebhookUrl,
                             MuteService muteService) {
        this.formatManager = fm;
        this.chatConfig = cfg;
        this.syncManager = sync;
        this.discordEnabled = discordEnabled;
        this.discordWebhookUrl = (discordWebhookUrl == null) ? "" : discordWebhookUrl.trim();
        this.muteService = muteService;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        // Respect chat.enabled in chat-format.yml
        final FileConfiguration conf = chatConfig.getCustomConfig();
        if (conf == null || !conf.getBoolean("chat.enabled", false)) {
            return; // Let vanilla/bukkit handle chat if plugin chat is disabled
        }

        final Player player = event.getPlayer();

        // Extra guard: if muted, cancel and stop here
        if (muteService != null && muteService.isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // We take over the chat pipeline
        event.setCancelled(true);

        String message = event.getMessage();
        String formatted = formatManager.formatMessage(player, message);

        // PlaceholderAPI expansion (best-effort)
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                formatted = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, formatted);
            }
        } catch (Throwable ignored) { }

        // Apply color codes for MC output
        final String mcOut = ChatColor.translateAlternateColorCodes('&', formatted);

        // ---- Channel routing start ----
        ChannelManager cm = OreoEssentials.get().getChannelManager();
        String speak = (cm != null) ? cm.getSpeakChannel(player) : null;
        if (speak == null) speak = "local"; // fallback

        ChatChannel ch = (cm != null) ? cm.get(speak) : null;
        if (ch == null || ch.isReadOnly() || !player.hasPermission(ch.getPermissionSpeak())) {
            player.sendMessage(ChatColor.RED + "You cannot speak in channel: " + ChatColor.YELLOW + speak);
            return;
        }

        final String channelLower = speak.toLowerCase(java.util.Locale.ROOT);

        // Deliver only to players who joined that channel (sync to main thread)
        Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
            var recipients = cm.recipientsFor(channelLower);
            for (Player r : recipients) {
                r.sendMessage(mcOut);
            }
        });

        // Cross-server publish only if this channel is crossServer
        try {
            if (syncManager != null && ch.isCrossServer()) {
                // Requires: ChatSyncManager.publishChannelMessage(UUID sender, String server, String player, String channel, String plainMessage)
                syncManager.publishChannelMessage(
                        player.getUniqueId(),
                        Bukkit.getServer().getName(),
                        player.getName(),
                        channelLower,
                        stripColors(formatted)
                );
            }
        } catch (Throwable ex) {
            Bukkit.getLogger().severe("[OreoEssentials] ChatSync publish failed: " + ex.getMessage());
        }

        // Optional Discord: only mirror GLOBAL if enabled
        if ("global".equalsIgnoreCase(channelLower)) {
            maybeSendToDiscord(player.getName(), stripColors(formatted));
        }
        // ---- Channel routing end ----
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
        String noAmp = s.replaceAll("(?i)&[0-9A-FK-OR]", "");
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', noAmp));
    }
}
