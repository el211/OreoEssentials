package fr.elias.oreoEssentials.rabbitmq;


import fr.elias.oreoEssentials.rabbitmq.IndividualPacketChannel;
import fr.elias.oreoEssentials.rabbitmq.MultiPacketChannel;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;

/**
 * Central place for well-known channels and channel factories.
 * Ensures GLOBAL is a true singleton across the JVM.
 */
public final class PacketChannels {
    private PacketChannels() {}

    /** Global broadcast channel (singleton). */
    public static final PacketChannel GLOBAL =
            IndividualPacketChannel.create("global");

    /** Create a single concrete channel wrapper. */
    public static PacketChannel individual(String channel) {
        return IndividualPacketChannel.create(channel);
    }

    /** Create a multi-channel wrapper from concrete channel names. */
    public static PacketChannel multiple(String... channels) {
        return MultiPacketChannel.create(channels);
    }

    /** Create a multi-channel wrapper by composing other PacketChannels. */
    public static PacketChannel multiple(PacketChannel... channels) {
        return MultiPacketChannel.create(channels);
    }
}

