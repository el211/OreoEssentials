// src/main/java/fr/elias/oreoEssentials/rabbitmq/namespace/PacketRegistry.java
package fr.elias.oreoEssentials.rabbitmq.namespace;

import fr.elias.oreoEssentials.rabbitmq.namespace.impl.HomesPacketNamespace;
import fr.elias.oreoEssentials.rabbitmq.namespace.impl.SpawnPacketNamespace;
import fr.elias.oreoEssentials.rabbitmq.namespace.impl.WarpsPacketNamespace;
import fr.elias.oreoEssentials.rabbitmq.packet.Packet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PacketRegistry {

    private final Map<Class<? extends Packet>, PacketDefinition<? extends Packet>> classToDefinition =
            new ConcurrentHashMap<>();
    private final Map<Long, PacketDefinition<?>> idToDefinition =
            new ConcurrentHashMap<>();

    public PacketRegistry() {
        // 1) Register your built-ins (if any)
        registerDefaults();

        // 2) Register cross-server teleport namespaces
        register(new HomesPacketNamespace());
        register(new WarpsPacketNamespace());
        register(new SpawnPacketNamespace());
    }

    /* -------- Public API -------- */

    public <T extends Packet> void register(PacketDefinition<T> definition) {
        classToDefinition.put(definition.getPacketClass(), definition);
        idToDefinition.put(definition.getRegistryId(), definition);
    }

    public void register(PacketNamespace namespace) {
        namespace.registerInto(this);
    }

    public PacketDefinition<? extends Packet> getDefinition(Class<? extends Packet> packetClass) {
        return classToDefinition.get(packetClass);
    }

    public PacketDefinition<?> getDefinition(long registryId) {
        return idToDefinition.get(registryId);
    }

    /* -------- Internals -------- */

    private void registerDefaults() {
        for (PacketNamespace ns : BuiltinPacketNamespaces.getNamespaces()) {
            register(ns);
        }
    }
}
