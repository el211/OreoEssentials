// File: src/main/java/fr/elias/oreoEssentials/rabbitmq/namespace/HomesPacketNamespace.java
package fr.elias.oreoEssentials.rabbitmq.namespace;

import fr.elias.oreoEssentials.rabbitmq.packet.impl.HomeTeleportRequestPacket;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.OtherHomeTeleportRequestPacket;

public final class HomesPacketNamespace extends PacketNamespace {
    public static final short NAMESPACE_ID = 0x0002;

    public static final int REG_HOME_TELEPORT_REQUEST        = 1;
    public static final int REG_OTHER_HOME_TELEPORT_REQUEST  = 2; // NEW

    public HomesPacketNamespace() {
        super(NAMESPACE_ID);
    }

    @Override
    protected void registerPackets() {
        registerPacket(REG_HOME_TELEPORT_REQUEST,       HomeTeleportRequestPacket.class,       HomeTeleportRequestPacket::new);
        registerPacket(REG_OTHER_HOME_TELEPORT_REQUEST, OtherHomeTeleportRequestPacket.class,  OtherHomeTeleportRequestPacket::new);
    }
}
