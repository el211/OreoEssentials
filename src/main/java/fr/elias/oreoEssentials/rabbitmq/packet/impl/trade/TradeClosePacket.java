// File: src/main/java/fr/elias/oreoEssentials/rabbitmq/packet/impl/trade/TradeClosePacket.java
package fr.elias.oreoEssentials.rabbitmq.packet.impl.trade;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

/**
 * Signals clients to force-close the trade UI for a specific participant (grantTo)
 * after a grant is issued. Sent on the GLOBAL channel.
 */
public final class TradeClosePacket extends Packet {
    private UUID sessionId;
    private UUID grantTo;

    public TradeClosePacket() {}

    public TradeClosePacket(UUID sessionId, UUID grantTo) {
        this.sessionId = sessionId;
        this.grantTo = grantTo;
    }

    public UUID getSessionId() { return sessionId; }
    public UUID getGrantTo()   { return grantTo; }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(sessionId);
        out.writeUUID(grantTo);
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        sessionId = in.readUUID();
        grantTo   = in.readUUID();
    }
}
