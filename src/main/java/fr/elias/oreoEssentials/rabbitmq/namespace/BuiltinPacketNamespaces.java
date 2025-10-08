// src/main/java/fr/elias/oreoEssentials/rabbitmq/namespace/BuiltinPacketNamespaces.java
package fr.elias.oreoEssentials.rabbitmq.namespace;

import fr.elias.oreoEssentials.rabbitmq.namespace.impl.EconomyPacketNamespace;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BuiltinPacketNamespaces {
    private BuiltinPacketNamespaces() {}

    private static final Map<Short, PacketNamespace> NAMESPACE_BY_ID = new ConcurrentHashMap<>();

    // Register your built-in namespaces here
    public static final PacketNamespace ECONOMY = add(new EconomyPacketNamespace());

    /** PacketRegistry will later call namespace.registerInto(this) lazily. */
    public static Collection<PacketNamespace> getNamespaces() {
        return NAMESPACE_BY_ID.values();
    }

    private static PacketNamespace add(PacketNamespace ns) {
        PacketNamespace prev = NAMESPACE_BY_ID.putIfAbsent(ns.getNamespaceId(), ns);
        if (prev != null) {
            throw new IllegalArgumentException(
                    "Namespace with id " + ns.getNamespaceId() + " is already registered"
            );
        }
        // DO NOT call ns.registerPackets() here — it’s lazy & protected.
        return ns;
    }
}
