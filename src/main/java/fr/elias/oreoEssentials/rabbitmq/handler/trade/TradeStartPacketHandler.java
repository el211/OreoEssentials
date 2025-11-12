// File: src/main/java/fr/elias/oreoEssentials/rabbitmq/handler/trade/TradeStartPacketHandler.java
package fr.elias.oreoEssentials.rabbitmq.handler.trade;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.trade.TradeStartPacket;

/**
 * Handles inbound TradeStartPacket messages.
 *
 * Each participating server receives this after one side accepts a trade invite.
 * The handler MUST NOT recompute the session ID; it uses the canonical sessionId
 * provided by the packet so both servers reference the same trade.
 *
 * The actual GUI + session lifecycle is delegated to TradeCrossServerBroker,
 * which in turn calls TradeService.openOrCreateCrossServerSession(...) on the
 * Bukkit main thread.
 */
public final class TradeStartPacketHandler implements PacketSubscriber<TradeStartPacket> {

    private final OreoEssentials plugin;

    public TradeStartPacketHandler(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(PacketChannel channel, TradeStartPacket packet) {
        if (packet == null) {
            return;
        }

        // Obtain broker (cross-server trade coordinator)
        var broker = plugin.getTradeBroker();
        if (broker == null) {
            plugin.getLogger().warning("[TRADE] <START> received but TradeCrossServerBroker is null.");
            return;
        }

        // Optional deep debug logging via TradeConfig.debugDeep (if TradeService is present)
        try {
            var svc = plugin.getTradeService();
            if (svc != null && svc.getConfig() != null && svc.getConfig().debugDeep) {
                plugin.getLogger().info("[TRADE] <START> recv"
                        + " sid=" + packet.getSessionId()
                        + " req=" + packet.getRequesterName() + "/" + packet.getRequesterId()
                        + " acc=" + packet.getAcceptorName() + "/" + packet.getAcceptorId()
                        + " ch=" + String.valueOf(channel));
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[TRADE] <START> debug log failed: " + t.getMessage());
        }

        try {
            // Delegate to broker – it will schedule to Bukkit main thread and
            // call TradeService.openOrCreateCrossServerSession(...)
            broker.handleRemoteStart(packet);
        } catch (Throwable t) {
            plugin.getLogger().severe("[TRADE] <START> handler failed: " + t.getMessage());
            t.printStackTrace();
        }
    }
}
