// File: src/main/java/fr/elias/oreoEssentials/rabbitmq/packet/impl/HomeTeleportRequestPacket.java
package fr.elias.oreoEssentials.rabbitmq.packet.impl;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.UUID;

public class HomeTeleportRequestPacket extends Packet {

    private UUID playerId;
    private String homeName;
    private String targetServer;

    public HomeTeleportRequestPacket() { }

    public HomeTeleportRequestPacket(UUID playerId, String homeName, String targetServer) {
        this.playerId = playerId;
        this.homeName = homeName;
        this.targetServer = targetServer;
    }

    // --- getters used by your handlers ---
    public UUID getPlayerId()       { return playerId; }
    public String getHomeName()     { return homeName; }
    public String getTargetServer() { return targetServer; }

    // --- required by Packet (match names/visibility exactly) ---
    @Override
    protected void write(FriendlyByteOutputStream out) {
        out.writeUUID(playerId);
        out.writeString(homeName != null ? homeName : "");
        out.writeString(targetServer != null ? targetServer : "");
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        this.playerId     = in.readUUID();
        this.homeName     = in.readString();
        this.targetServer = in.readString();
    }
}
