package fr.elias.oreoEssentials.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DiscordWebhook {
    private final String webhookUrl;
    public DiscordWebhook(String webhookUrl) { this.webhookUrl = webhookUrl; }

    public void sendMessage(String username, String content) {
        try {
            if (webhookUrl == null || webhookUrl.isEmpty()) return;
            String plain = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', content));
            URL url = new URL(webhookUrl);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setDoOutput(true);
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            String payload = String.format("{\"username\":\"%s\",\"content\":\"%s\"}", username.replace("\"","'"), plain.replace("\"","'"));
            try (OutputStream os = con.getOutputStream()) { os.write(payload.getBytes()); }
            int code = con.getResponseCode();
            if (code != 204) Bukkit.getLogger().warning("Discord webhook failed: HTTP " + code);
            con.disconnect();
        } catch (Exception e) {
            Bukkit.getLogger().severe("Discord webhook error: " + e.getMessage());
        }
    }
}
