// File: src/main/java/fr/elias/oreoEssentials/util/ChatSyncManager.java
package fr.elias.oreoEssentials.util;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.services.MuteService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public class ChatSyncManager {
    private static final String EXCHANGE_NAME = "chat_sync";
    private static final UUID SERVER_ID = UUID.randomUUID();

    private final MuteService muteService; // may be null
    private Connection rabbitConnection;
    private Channel rabbitChannel;
    private boolean enabled;

    /* ----------------------------- Ctors ----------------------------- */

    /** Back-compat constructor (no mute checks on receive). */
    public ChatSyncManager(boolean enabled, String rabbitUri) {
        this(enabled, rabbitUri, null);
    }

    /** Preferred constructor with local mute checks on receive. */
    public ChatSyncManager(boolean enabled, String rabbitUri, MuteService muteService) {
        this.enabled = enabled;
        this.muteService = muteService;
        if (!enabled) return;

        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setUri(rabbitUri);
            // optional: automatic recovery helps if broker restarts
            factory.setAutomaticRecoveryEnabled(true);
            factory.setNetworkRecoveryInterval(5000);

            rabbitConnection = factory.newConnection();
            rabbitChannel = rabbitConnection.createChannel();
            rabbitChannel.exchangeDeclare(EXCHANGE_NAME, "fanout");
        } catch (Exception e) {
            this.enabled = false;
            Bukkit.getLogger().severe("[OreoEssentials] ChatSync init failed: " + e.getMessage());
            safeClose();
        }
    }

    /* ----------------------------- Chat publish ----------------------------- */

    /**
     * Publish a chat message. Includes sender UUID so remote servers can apply their mute rules.
     * Payload: serverUUID;;playerUUID;;base64(name);;base64(message)
     */
    public void publishMessage(UUID playerId, String serverName, String playerName, String message) {
        if (!enabled) return;
        try {
            String payload =
                    SERVER_ID + ";;" +
                            playerId + ";;" +
                            b64(playerName) + ";;" +
                            b64(message);
            rabbitChannel.basicPublish(EXCHANGE_NAME, "", null, payload.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Bukkit.getLogger().warning("[OreoEssentials] ChatSync publish failed: " + e.getMessage());
        }
    }

    /** Legacy publish (without UUID) – kept to avoid breaking old callers; discouraged. */
    @Deprecated
    public void publishMessage(String serverName, String playerName, String message) {
        publishMessage(new UUID(0L, 0L), serverName, playerName, message);
    }

    /* ----------------------------- Control broadcast ----------------------------- */

    /** Broadcast a mute to all servers. */
    public void broadcastMute(UUID playerId, long untilEpochMillis, String reason, String by) {
        if (!enabled) return;
        try {
            // CTRL;;MUTE;;serverId;;playerUUID;;untilMillis;;b64(reason);;b64(by)
            String payload = "CTRL;;MUTE;;" + SERVER_ID + ";;" + playerId + ";;" + untilEpochMillis
                    + ";;" + b64(nullSafe(reason)) + ";;" + b64(nullSafe(by));
            rabbitChannel.basicPublish(EXCHANGE_NAME, "", null, payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Bukkit.getLogger().warning("[OreoEssentials] ChatSync MUTE broadcast failed: " + e.getMessage());
        }
    }

    /** Broadcast an unmute to all servers. */
    public void broadcastUnmute(UUID playerId) {
        if (!enabled) return;
        try {
            // CTRL;;UNMUTE;;serverId;;playerUUID
            String payload = "CTRL;;UNMUTE;;" + SERVER_ID + ";;" + playerId;
            rabbitChannel.basicPublish(EXCHANGE_NAME, "", null, payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Bukkit.getLogger().warning("[OreoEssentials] ChatSync UNMUTE broadcast failed: " + e.getMessage());
        }
    }

    /* ----------------------------- Subscribe/receive ----------------------------- */

    public void subscribeMessages() {
        if (!enabled) return;
        try {
            String q = rabbitChannel.queueDeclare().getQueue();
            rabbitChannel.queueBind(q, EXCHANGE_NAME, "");

            DeliverCallback cb = (tag, delivery) -> {
                String msg = new String(delivery.getBody(), StandardCharsets.UTF_8);

                // ---- Control messages (mute/unmute) ----
                if (msg.startsWith("CTRL;;")) {
                    // We expect either:
                    //   CTRL;;UNMUTE;;serverId;;playerUUID
                    //   CTRL;;MUTE;;serverId;;playerUUID;;untilMillis;;b64(reason);;b64(by)
                    String[] p = msg.split(";;", 7);
                    if (p.length >= 4) {
                        String action = p[1];           // "MUTE" or "UNMUTE"
                        String originServer = p[2];     // can be ignored (idempotent)
                        String uuidStr = p[3];

                        try {
                            UUID target = UUID.fromString(uuidStr);
                            if ("UNMUTE".equals(action)) {
                                Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                                    if (muteService != null) muteService.unmute(target);
                                });
                            } else if ("MUTE".equals(action) && p.length >= 7) {
                                long until = Long.parseLong(p[4]);
                                String reason = new String(Base64.getDecoder().decode(p[5]), StandardCharsets.UTF_8);
                                String by     = new String(Base64.getDecoder().decode(p[6]), StandardCharsets.UTF_8);
                                Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> {
                                    if (muteService != null) muteService.mute(target, until, reason, by);
                                });
                            }
                        } catch (Exception ignored) { /* bad payload */ }
                    }
                    return; // do not fall through to chat handling
                }

                // ---- Chat messages ----
                String[] split = msg.split(";;", 4);
                if (split.length != 4) return;

                String originServerId = split[0];
                String senderUuidStr  = split[1];
                String nameB64        = split[2]; // not used currently, kept for future
                String msgB64         = split[3];

                // Ignore loopback
                if (SERVER_ID.toString().equals(originServerId)) return;

                UUID senderUuid = null;
                try { senderUuid = UUID.fromString(senderUuidStr); } catch (Exception ignored) { }

                // If we have muteService and a valid UUID, drop message when that UUID is muted locally
                if (muteService != null && senderUuid != null && muteService.isMuted(senderUuid)) return;

                String decodedMessage;
                try {
                    decodedMessage = new String(Base64.getDecoder().decode(msgB64), StandardCharsets.UTF_8);
                } catch (Exception ex) {
                    decodedMessage = "[remote] <decode error>";
                }

                // Broadcast on main thread; message is expected to use '&' color codes
                final String toSend = ChatColor.translateAlternateColorCodes('&', decodedMessage);
                Bukkit.getScheduler().runTask(OreoEssentials.get(), () -> Bukkit.broadcastMessage(toSend));
            };

            rabbitChannel.basicConsume(q, true, cb, tag -> {});
        } catch (Exception e) {
            Bukkit.getLogger().warning("[OreoEssentials] ChatSync subscribe failed: " + e.getMessage());
        }
    }

    /* ----------------------------- Lifecycle ----------------------------- */

    public void close() {
        safeClose();
    }

    private void safeClose() {
        try { if (rabbitChannel != null && rabbitChannel.isOpen()) rabbitChannel.close(); } catch (Exception ignored) {}
        try { if (rabbitConnection != null && rabbitConnection.isOpen()) rabbitConnection.close(); } catch (Exception ignored) {}
    }

    /* ----------------------------- Helpers ----------------------------- */

    private static String b64(String s) {
        if (s == null) s = "";
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String nullSafe(String s) {
        return (s == null) ? "" : s;
    }
}
