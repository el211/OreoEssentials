// File: src/main/java/fr/elias/oreoEssentials/rabbitmq/packet/impl/TpaSummonPacket.java
package fr.elias.oreoEssentials.rabbitmq.packet.impl;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

/**
 * Sent from TARGET server -> REQUESTER server when /tpaccept is executed on the target server.
 * Instructs the requester’s current server to switch them to destServer (target’s server).
 */
public class TpaSummonPacket extends Packet {

    private UUID requesterUuid;
    private String destServer;

    public TpaSummonPacket() {}

    public TpaSummonPacket(UUID requesterUuid, String destServer) {
        this.requesterUuid = requesterUuid;
        this.destServer = destServer;
    }

    /* --- getters --- */
    public UUID getRequesterUuid() { return requesterUuid; }
    public String getDestServer()  { return destServer; }

    /* --- Packet IO --- */
    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(requesterUuid);
        out.writeString(destServer != null ? destServer : "");
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        this.requesterUuid = in.readUUID();
        this.destServer    = in.readString();
    }
}
