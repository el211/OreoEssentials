package fr.elias.oreoEssentials.rabbitmq.packet;


import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.namespace.PacketDefinition;
import fr.elias.oreoEssentials.rabbitmq.namespace.PacketRegistry;
import fr.elias.oreoEssentials.rabbitmq.packet.event.IncomingPacketListener;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSender;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriptionQueue;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PacketManager implements IncomingPacketListener {

    private final OreoEssentials plugin;
    private final PacketSender sender;

    private final Map<Class<? extends Packet>, PacketSubscriptionQueue<? extends Packet>> subscriptions;
    private final PacketRegistry packetRegistry;

    private boolean initialized;

    public PacketManager(OreoEssentials plugin, PacketSender sender) {
        this.plugin = plugin;
        this.sender = sender;
        this.subscriptions = new ConcurrentHashMap<>();
        this.packetRegistry = new PacketRegistry();
    }

    public void init() {
        initialized = true;
        this.sender.registerListener(this);
    }

    public void subscribeChannel(PacketChannel channel) {
        this.sender.registerChannel(channel);
    }

    public void sendPacket(PacketChannel target, Packet packet) {
        PacketDefinition<?> definition = this.packetRegistry.getDefinition(packet.getClass());

        FriendlyByteOutputStream outputStream = new FriendlyByteOutputStream();
        outputStream.writeLong(definition.getRegistryId());
        packet.writeData(outputStream);
        this.sender.sendPacket(target, outputStream.toByteArray());
    }

    public void sendPacket(Packet packet) {
        sendPacket(PacketChannels.GLOBAL, packet);
    }

    public <T extends Packet> void subscribe(Class<T> packetClass, PacketSubscriber<T> subscriber) {
        PacketSubscriptionQueue<T> queue = (PacketSubscriptionQueue<T>) this.subscriptions.computeIfAbsent(packetClass, key -> new PacketSubscriptionQueue<>(packetClass));
        queue.subscribe(subscriber);
    }

    @Override
    public void onReceive(PacketChannel channel, byte[] content) {
        FriendlyByteInputStream outputStream = new FriendlyByteInputStream(content);
        long registryId = outputStream.readLong();

        PacketDefinition<?> definition = this.packetRegistry.getDefinition(registryId);

        if (definition == null) {
            this.log("Received packet with no definition: " + registryId);
            return;
        }

        Packet packet = definition.getProvider().createPacket();
        packet.readData(outputStream);

        this.dispatch(channel, packet);
    }

    private <T extends Packet> void dispatch(PacketChannel channel, T packet) {
        PacketSubscriptionQueue<T> queue = (PacketSubscriptionQueue<T>) this.subscriptions.get(packet.getClass());

        if (queue == null) {
            this.log("Received packet with no subscribers: " + packet.getClass().getName());
            return;
        }

        queue.dispatch(channel, packet);
    }

    private void log(String message) {
        this.plugin.getLogger().warning(message);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void close() {
        initialized = false;
        sender.close();
    }
}

