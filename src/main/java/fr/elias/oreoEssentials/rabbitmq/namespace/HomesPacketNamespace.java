// File: src/main/java/fr/elias/oreoEssentials/rabbitmq/namespace/HomesPacketNamespace.java
package fr.elias.oreoEssentials.rabbitmq.namespace;

import fr.elias.oreoEssentials.rabbitmq.packet.impl.HomeTeleportRequestPacket;

public final class HomesPacketNamespace extends PacketNamespace {

    /** Unique namespace id (short). Must be the same across all servers. */
    public static final short NAMESPACE_ID = 0x0002;

    /** Stable, unique packet id within this namespace. */
    public static final int REG_HOME_TELEPORT_REQUEST = 1;

    public HomesPacketNamespace() {
        super(NAMESPACE_ID);
    }

    @Override
    protected void registerPackets() {
        // Registers: (packetId, class, provider)
        registerPacket(
                REG_HOME_TELEPORT_REQUEST,
                HomeTeleportRequestPacket.class,
                HomeTeleportRequestPacket::new
        );
    }
}
