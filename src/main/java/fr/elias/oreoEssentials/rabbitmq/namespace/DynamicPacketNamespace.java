// File: src/main/java/fr/elias/oreoEssentials/rabbitmq/namespace/DynamicPacketNamespace.java
package fr.elias.oreoEssentials.rabbitmq.namespace;

public final class DynamicPacketNamespace extends PacketNamespace {

    /** Pick a high, unused namespace id. Ensure no other namespace uses this. */
    public static final short ID = (short) 32000;

    public DynamicPacketNamespace() {
        super(ID); // pass the id to the base; do NOT override getNamespaceId()
    }

    /** No predefined packets — dynamic ones are added directly by PacketRegistry. */
    @Override
    protected void registerPackets() {
        // Intentionally empty
    }
}
