package fr.elias.oreoEssentials.rabbitmq;


import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;

public final class PacketChannels {

    private PacketChannels() {
    }

    public static final PacketChannel GLOBAL = PacketChannel.individual("global");

}
