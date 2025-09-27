package fr.elias.oreoEssentials.rabbitmq.namespace;


import fr.elias.oreoEssentials.rabbitmq.packet.Packet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PacketRegistry {

    private final Map<Class<? extends Packet>, PacketDefinition<? extends Packet>> packetDefinitions = new ConcurrentHashMap<>();
    private final Map<Long, PacketDefinition<?>> idToDefinition = new ConcurrentHashMap<>();

    public PacketRegistry() {
        registerDefaults();
    }

    public <T extends Packet> void register(PacketDefinition<T> definition) {
        packetDefinitions.put(definition.getPacketClass(), definition);
        idToDefinition.put(definition.getRegistryId(), definition);
    }

    public void register(PacketNamespace namespace) {
        for (PacketDefinition<?> definition : namespace.getDefinitions()) {
            register(definition);
        }
    }

    public PacketDefinition<? extends Packet> getDefinition(Class<? extends Packet> packetClass) {
        return packetDefinitions.get(packetClass);
    }

    public PacketDefinition<?> getDefinition(long registryId) {
        return idToDefinition.get(registryId);
    }

    private void registerDefaults() {
        for (PacketNamespace namespace : BuiltinPacketNamespaces.getNamespaces()) {
            register(namespace);
        }
    }


}
