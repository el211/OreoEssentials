package fr.elias.oreoEssentials.rabbitmq.namespace.impl;

import fr.elias.oreoEssentials.rabbitmq.namespace.PacketNamespace;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.HomeTeleportRequestPacket;

public final class HomesPacketNamespace extends PacketNamespace {

    public static final int HOME_TP_REQ_ID = 1001;

    public HomesPacketNamespace() {
        super((short) 10); // arbitrary, unique per namespace
    }

    @Override
    protected void registerPackets() {
        registerPacket(HOME_TP_REQ_ID, HomeTeleportRequestPacket.class, HomeTeleportRequestPacket::new);
    }
}
