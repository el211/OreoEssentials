// File: src/main/java/fr/elias/oreoEssentials/rabbitmq/namespace/PacketRegistry.java
package fr.elias.oreoEssentials.rabbitmq.namespace;

import fr.elias.oreoEssentials.rabbitmq.packet.Packet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry that maps:
 *  - Packet class -> PacketDefinition (for sending)
 *  - Registry ID  -> PacketDefinition (for receiving)
 *
 * IMPORTANT: All servers in the network MUST use the same registry IDs.
 */
public class PacketRegistry {

    private final Map<Class<? extends Packet>, PacketDefinition<? extends Packet>> classToDefinition =
            new ConcurrentHashMap<>();
    private final Map<Long, PacketDefinition<?>> idToDefinition =
            new ConcurrentHashMap<>();

    public PacketRegistry() {
        // 1) Register built-in namespaces
        registerDefaults();
        // 2) Register our homes namespace (HomeTeleportRequestPacket lives here)
        register(new HomesPacketNamespace());
    }

    /* ------------------------- Public API ------------------------- */

    public <T extends Packet> void register(PacketDefinition<T> definition) {
        classToDefinition.put(definition.getPacketClass(), definition);
        // PacketManager reads a long; widen int id to long for the key
        long idAsLong = definition.getRegistryId();
        idToDefinition.put(idAsLong, definition);
    }

    /** Register all packet definitions from a namespace. */
    public void register(PacketNamespace namespace) {
        // Let the namespace populate itself once, then copy its defs into this registry
        namespace.registerInto(this);
    }

    /** Lookup used when SENDING (class -> definition). */
    public PacketDefinition<? extends Packet> getDefinition(Class<? extends Packet> packetClass) {
        return classToDefinition.get(packetClass);
    }

    /** Lookup used when RECEIVING (id -> definition). */
    public PacketDefinition<?> getDefinition(long registryId) {
        return idToDefinition.get(registryId);
    }

    /* ------------------------- Internals ------------------------- */

    private void registerDefaults() {
        for (PacketNamespace ns : BuiltinPacketNamespaces.getNamespaces()) {
            register(ns);
        }
    }
}
