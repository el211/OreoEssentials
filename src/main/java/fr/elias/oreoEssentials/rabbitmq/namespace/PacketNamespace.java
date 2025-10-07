// File: src/main/java/fr/elias/oreoEssentials/rabbitmq/namespace/PacketNamespace.java
package fr.elias.oreoEssentials.rabbitmq.namespace;

import com.google.common.collect.Sets;
import fr.elias.oreoEssentials.rabbitmq.packet.Packet;

import java.util.Collection;

/**
 * A namespace groups packet definitions.
 *
 * Usage pattern:
 *  - Subclass implements {@link #registerPackets()} and calls {@link #registerPacket(int, Class, PacketProvider)} for each packet.
 *  - The first time someone asks for definitions (or calls ensureRegistered / registerInto),
 *    we run registerPackets() exactly once and cache the results.
 */
public abstract class PacketNamespace {

    private final short namespaceId;
    private final Collection<PacketDefinition<?>> definitions = Sets.newConcurrentHashSet();

    // guard to ensure registerPackets() is executed once
    private volatile boolean registered = false;

    protected PacketNamespace(short namespaceId) {
        this.namespaceId = namespaceId;
    }

    /**
     * Subclasses call this inside {@link #registerPackets()} to add packets to the namespace.
     */
    protected final <T extends Packet> void registerPacket(int packetId, Class<T> packetClass, PacketProvider<T> provider) {
        PacketDefinition<T> definition = new PacketDefinition<>(packetId, packetClass, provider, this);
        definitions.add(definition);
    }

    /**
     * Implementations must register all their packets here (via {@link #registerPacket(...)})
     * Do NOT call this yourself; it is invoked lazily by ensureRegistered().
     */
    protected abstract void registerPackets();

    /**
     * Ensures {@link #registerPackets()} has been called exactly once.
     */
    public final void ensureRegistered() {
        if (!registered) {
            synchronized (this) {
                if (!registered) {
                    registerPackets();
                    registered = true;
                }
            }
        }
    }

    public final short getNamespaceId() {
        return namespaceId;
    }

    /**
     * Returns all definitions for this namespace.
     * Lazily triggers {@link #registerPackets()} on the first call.
     */
    public final Collection<PacketDefinition<?>> getDefinitions() {
        ensureRegistered();
        return definitions;
    }

    /**
     * Convenience hook: register all definitions from this namespace into a registry.
     * (Optional to use—handy for {@code PacketRegistry.register(namespace)}).
     */
    public final void registerInto(PacketRegistry registry) {
        ensureRegistered();
        for (PacketDefinition<?> def : definitions) {
            registry.register(def);
        }
    }
}
