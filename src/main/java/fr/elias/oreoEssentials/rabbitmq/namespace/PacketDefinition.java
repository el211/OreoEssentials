package fr.elias.oreoEssentials.rabbitmq.namespace;


import fr.elias.oreoEssentials.rabbitmq.packet.Packet;

public class PacketDefinition<T extends Packet> {

    private final int packetId;
    private final Class<T> packetClass;
    private final PacketProvider<T> provider;
    private final PacketNamespace namespace;

    private final long registryId;

    public PacketDefinition(int packetId, Class<T> packetClass, PacketProvider<T> provider, PacketNamespace namespace) {
        this.packetId = packetId;
        this.packetClass = packetClass;
        this.provider = provider;
        this.namespace = namespace;

        this.registryId = createRegistryId(namespace.getNamespaceId(), packetId);
    }

    public Class<T> getPacketClass() {
        return packetClass;
    }

    public int getPacketId() {
        return packetId;
    }

    public PacketProvider<T> getProvider() {
        return provider;
    }

    public PacketNamespace getNamespace() {
        return namespace;
    }

    public long getRegistryId() {
        return registryId;
    }

    private long createRegistryId(short namespaceId, int packetId) {
        return ((long) namespaceId << 32) | packetId;
    }
}

