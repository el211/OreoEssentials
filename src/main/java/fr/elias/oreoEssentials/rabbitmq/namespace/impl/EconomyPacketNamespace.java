package fr.elias.oreoEssentials.rabbitmq.namespace.impl;


import fr.elias.oreoEssentials.rabbitmq.namespace.PacketNamespace;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.PlayerJoinPacket;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.PlayerQuitPacket;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.SendRemoteMessagePacket;

public class EconomyPacketNamespace extends PacketNamespace {

    public EconomyPacketNamespace() {
        super((short) 1);
    }

    @Override
    protected void registerPackets() {
        registerPacket(0, PlayerJoinPacket.class, PlayerJoinPacket::new);
        registerPacket(1, PlayerQuitPacket.class, PlayerQuitPacket::new);
        registerPacket(2, SendRemoteMessagePacket.class, SendRemoteMessagePacket::new);
    }
}
