package fr.elias.oreoEssentials.rabbitmq.handler;


import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.PlayerQuitPacket;

import java.util.UUID;

public class PlayerQuitPacketHandler implements PacketSubscriber<PlayerQuitPacket> {

    private final OreoEssentials plugin;

    public PlayerQuitPacketHandler(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(PacketChannel channel, PlayerQuitPacket packet) {
        UUID playerId = packet.getPlayerId();

        if (playerId == null) {
            plugin.getLogger().warning("[OreoEssentials] ⚠ Received PlayerQuitPacket with null UUID! Skipping removal.");
            return;
        }

        // 🛡 Prevent crash if player isn't cached
        if (plugin.getOfflinePlayerCache().contains(playerId)) {
            plugin.getOfflinePlayerCache().remove(playerId);
            plugin.getLogger().info("📨 Received PlayerQuitPacket: " + playerId + " (removed from cache)");
        } else {
            plugin.getLogger().warning("⚠ Received PlayerQuitPacket for unknown UUID: " + playerId + " — not in cache.");
        }
    }
}

