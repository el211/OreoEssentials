// src/main/java/fr/elias/oreoEssentials/rabbitmq/namespace/impl/CrossInvPacketNamespace.java
package fr.elias.oreoEssentials.rabbitmq.namespace.impl;

import fr.elias.oreoEssentials.rabbitmq.namespace.PacketNamespace;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.CrossInvPacket;

public final class CrossInvPacketNamespace extends PacketNamespace {

    // Unique ids (namespace is a short in your base class)
    public static final short NS_ID = 91;      // any unused short is fine
    public static final int   CROSS_INV_PACKET_ID = 9101;

    public CrossInvPacketNamespace() {
        super(NS_ID);
    }

    @Override
    protected void registerPackets() {
        // This helper builds PacketDefinition<>(id, class, provider, this) for you
        registerPacket(CROSS_INV_PACKET_ID, CrossInvPacket.class, CrossInvPacket::new);
    }
}
