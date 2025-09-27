package fr.elias.oreoEssentials.rabbitmq.namespace;


import com.google.common.collect.Sets;
import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import java.util.Collection;

public abstract class PacketNamespace {

    private final short namespaceId;
    private final Collection<PacketDefinition<?>> definitions = Sets.newConcurrentHashSet();

    protected PacketNamespace(short namespaceId) {
        this.namespaceId = namespaceId;
    }

    protected <T extends Packet> void registerPacket(int packetId, Class<T> packetClass, PacketProvider<T> provider) {
        PacketDefinition<T> definition = new PacketDefinition<>(packetId, packetClass, provider, this);
        definitions.add(definition);
    }

    protected abstract void registerPackets();

    public short getNamespaceId() {
        return namespaceId;
    }

    public Collection<PacketDefinition<?>> getDefinitions() {
        return definitions;
    }
}
