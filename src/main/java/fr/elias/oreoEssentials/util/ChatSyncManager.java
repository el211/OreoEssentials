package fr.elias.oreoEssentials.util;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

public class ChatSyncManager {
    private static final String EXCHANGE_NAME = "chat_sync";
    private static final UUID SERVER_ID = UUID.randomUUID();

    private Connection rabbitConnection;
    private Channel rabbitChannel;
    private boolean enabled;

    public ChatSyncManager(boolean enabled, String rabbitUri) {
        this.enabled = enabled;
        if (!enabled) return;

        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setUri(rabbitUri);
            rabbitConnection = factory.newConnection();
            rabbitChannel = rabbitConnection.createChannel();
            rabbitChannel.exchangeDeclare(EXCHANGE_NAME, "fanout");
        } catch (Exception e) {
            // Fail closed: disable sync but keep server running
            this.enabled = false;
            Bukkit.getLogger().severe("[OreoEssentials] ChatSync init failed: " + e.getMessage());
            safeClose();
        }
    }

    public void publishMessage(String serverName, String playerName, String message) {
        if (!enabled) return;
        try {
            String payload = SERVER_ID + ";;" + message;
            rabbitChannel.basicPublish(EXCHANGE_NAME, "", null, payload.getBytes());
        } catch (IOException e) {
            Bukkit.getLogger().warning("[OreoEssentials] ChatSync publish failed: " + e.getMessage());
        }
    }

    public void subscribeMessages() {
        if (!enabled) return;
        try {
            String q = rabbitChannel.queueDeclare().getQueue();
            rabbitChannel.queueBind(q, EXCHANGE_NAME, "");
            DeliverCallback cb = (tag, delivery) -> {
                String msg = new String(delivery.getBody());
                String[] split = msg.split(";;", 2);
                if (split.length != 2) return;
                String server = split[0];
                String formatted = split[1];

                if (!server.equals(SERVER_ID.toString())) {
                    Bukkit.getScheduler().runTask(OreoEssentials.get(),
                            () -> Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', formatted)));
                }
            };
            rabbitChannel.basicConsume(q, true, cb, tag -> {});
        } catch (Exception e) {
            Bukkit.getLogger().warning("[OreoEssentials] ChatSync subscribe failed: " + e.getMessage());
        }
    }

    public void close() {
        safeClose();
    }

    private void safeClose() {
        try { if (rabbitChannel != null && rabbitChannel.isOpen()) rabbitChannel.close(); } catch (Exception ignored) {}
        try { if (rabbitConnection != null && rabbitConnection.isOpen()) rabbitConnection.close(); } catch (Exception ignored) {}
    }
}
