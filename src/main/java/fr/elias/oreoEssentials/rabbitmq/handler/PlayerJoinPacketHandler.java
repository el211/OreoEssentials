package fr.elias.oreoEssentials.rabbitmq.handler;


import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.PlayerJoinPacket;

import java.util.UUID;

public class PlayerJoinPacketHandler implements PacketSubscriber<PlayerJoinPacket> {

    private final OreoEssentials plugin;

    public PlayerJoinPacketHandler(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(PacketChannel channel, PlayerJoinPacket packet) {
        UUID uuid = packet.getPlayerId();
        String name = packet.getPlayerName();

        // 🧠 Log join packets for Bedrock/Java sync
        plugin.getLogger().info("📨 Received PlayerJoinPacket from channel '" + channel + "'");
        plugin.getLogger().info("👤 Player: " + name + " (UUID: " + uuid + ")");

        // ✅ Add to shared offline player cache
        plugin.getOfflinePlayerCache().add(name, uuid);
    }
}
