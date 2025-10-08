// src/main/java/fr/elias/oreoEssentials/rabbitmq/namespace/PacketProvider.java
package fr.elias.oreoEssentials.rabbitmq.namespace;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;

@FunctionalInterface
public interface PacketProvider<T extends Packet> {
    T createPacket();
}
