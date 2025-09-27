package fr.elias.oreoEssentials.rabbitmq.namespace;


import fr.elias.oreoEssentials.rabbitmq.packet.Packet;

public interface PacketProvider<T extends Packet> {

    T createPacket();
}
