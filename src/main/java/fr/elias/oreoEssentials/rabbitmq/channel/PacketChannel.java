// File: src/main/java/fr/elias/oreoEssentials/rabbitmq/channel/PacketChannel.java
package fr.elias.oreoEssentials.rabbitmq.channel;

import fr.elias.oreoEssentials.rabbitmq.IndividualPacketChannel;
import fr.elias.oreoEssentials.rabbitmq.MultiPacketChannel;

import java.util.Iterator;

/**
 * Represents one or more underlying transport channel names.
 * Implementations should be immutable.
 */
public interface PacketChannel extends Iterable<String> {

    /**
     * Create a channel that maps to a single concrete queue/topic name.
     */
    static PacketChannel individual(String name) {
        return IndividualPacketChannel.create(name);
    }

    /**
     * Create a channel that maps to multiple concrete queue/topic names.
     */
    static PacketChannel multiple(String... names) {
        return MultiPacketChannel.create(names);
    }

    /**
     * Create a composite channel from other PacketChannel instances.
     */
    static PacketChannel multiple(PacketChannel... channels) {
        return MultiPacketChannel.create(channels);
    }

    /**
     * Returns the iterable of concrete channel names that this logical channel represents.
     */
    Iterable<String> getChannels();

    @Override
    default Iterator<String> iterator() {
        return getChannels().iterator();
    }
}
