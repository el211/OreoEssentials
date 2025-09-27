package fr.elias.oreoEssentials.rabbitmq.channel;

import fr.elias.oreoEssentials.rabbitmq.IndividualPacketChannel;
import fr.elias.oreoEssentials.rabbitmq.MultiPacketChannel;

import java.util.Iterator;

public interface PacketChannel extends Iterable<String> {
    static PacketChannel individual(String channel) { return IndividualPacketChannel.create(channel); }
    static PacketChannel multiple(String... channels) { return MultiPacketChannel.create(channels); }
    static PacketChannel multiple(PacketChannel... channels) { return MultiPacketChannel.create(channels); }

    Iterable<String> getChannels();

    @Override
    default Iterator<String> iterator() { return getChannels().iterator(); }
}