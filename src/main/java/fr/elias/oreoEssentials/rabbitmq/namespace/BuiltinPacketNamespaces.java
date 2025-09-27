package fr.elias.oreoEssentials.rabbitmq.namespace;


import fr.elias.oreoEssentials.rabbitmq.namespace.impl.EconomyPacketNamespace;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BuiltinPacketNamespaces {

    private BuiltinPacketNamespaces() {
    }

    private static final Map<Short, PacketNamespace> NAMESPACE_BY_ID = new ConcurrentHashMap<>();

    public static final PacketNamespace ECONOMY = register(new EconomyPacketNamespace());

    public static Collection<PacketNamespace> getNamespaces() {
        return NAMESPACE_BY_ID.values();
    }

    private static PacketNamespace register(PacketNamespace namespace) {
        if (NAMESPACE_BY_ID.containsKey(namespace.getNamespaceId())) {
            throw new IllegalArgumentException("Namespace with id " + namespace.getNamespaceId() + " is already registered");
        }

        namespace.registerPackets();
        NAMESPACE_BY_ID.put(namespace.getNamespaceId(), namespace);
        return namespace;
    }
}

