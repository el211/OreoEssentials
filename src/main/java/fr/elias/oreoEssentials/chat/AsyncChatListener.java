package fr.elias.oreoEssentials.chat;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.ChatSyncManager;
import fr.elias.oreoEssentials.util.DiscordWebhook;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class AsyncChatListener implements Listener {
    private final FormatManager formatManager;
    private final CustomConfig customConfig;
    private final ChatSyncManager chatSyncManager;
    private final DiscordWebhook discordWebhook;

    public AsyncChatListener(FormatManager formatManager, CustomConfig customConfig, ChatSyncManager chatSyncManager, String discordWebhookUrl) {
        this.formatManager = formatManager;
        this.customConfig = customConfig;
        this.chatSyncManager = chatSyncManager;
        this.discordWebhook = new DiscordWebhook(discordWebhookUrl);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!customConfig.getCustomConfig().getBoolean("chat.enabled", false)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        String message = event.getMessage();

        String formatted = formatManager.formatMessage(player, message);

        // PlaceholderAPI placeholders inside the format (optional)
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                formatted = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, formatted);
            }
        } catch (Throwable ignored) {}

        // Broadcast locally
        String mc = ChatColor.translateAlternateColorCodes('&', formatted);
        Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> Bukkit.broadcastMessage(mc));

        // Sync to RabbitMQ (if enabled)
        try { chatSyncManager.publishMessage(Bukkit.getServer().getName(), player.getName(), formatted); }
        catch (Exception ex) { Bukkit.getLogger().severe("ChatSync publish failed: " + ex.getMessage()); }

        // Send to Discord
        discordWebhook.sendMessage(player.getName(), formatted);
    }
}
