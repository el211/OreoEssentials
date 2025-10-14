package fr.elias.oreoEssentials.rabbitmq.packet.impl;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

public final class CrossInvPacket extends Packet {
    private String json;

    public CrossInvPacket() {
        // no-arg required by registry
    }

    public CrossInvPacket(String json) {
        this.json = json;
    }

    public String getJson() {
        return json;
    }

    @Override
    protected void read(FriendlyByteInputStream in) {
        // packetId already read in Packet.readData(...)
        this.json = in.readString();
    }

    @Override
    protected void write(FriendlyByteOutputStream out) {
        // Packet.writeData(...) already wrote packetId
        out.writeString(json != null ? json : "");
    }
}
