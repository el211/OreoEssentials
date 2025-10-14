package fr.elias.oreoEssentials.rabbitmq.packet;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.namespace.PacketDefinition;
import fr.elias.oreoEssentials.rabbitmq.namespace.PacketRegistry;
import fr.elias.oreoEssentials.rabbitmq.packet.event.IncomingPacketListener;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSender;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriber;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSubscriptionQueue;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.HomeTeleportRequestPacket;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteInputStream;
import fr.elias.oreoEssentials.rabbitmq.stream.FriendlyByteOutputStream;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PacketManager implements IncomingPacketListener {

    private final OreoEssentials plugin;
    private final PacketSender sender;

    private final Map<Class<? extends Packet>, PacketSubscriptionQueue<? extends Packet>> subscriptions;
    private final PacketRegistry packetRegistry;

    private volatile boolean initialized;

    public PacketManager(OreoEssentials plugin, PacketSender sender) {
        this.plugin = plugin;
        this.sender = sender;
        this.subscriptions = new ConcurrentHashMap<>();
        this.packetRegistry = new PacketRegistry();
    }

    public void init() {
        initialized = true;
        this.sender.registerListener(this);
        // Optional: debug trace for init
        dbg("[PM/INIT@" + serverName() + "] PacketManager initialized and listener registered.");
    }

    public void subscribeChannel(PacketChannel channel) {
        this.sender.registerChannel(channel);
        dbg("[PM/CHAN@" + serverName() + "] Subscribed channel " + renderChannel(channel));
    }

    /* ====================== SEND ====================== */

    public void sendPacket(PacketChannel target, Packet packet) {
        PacketDefinition<?> definition = this.packetRegistry.getDefinition(packet.getClass());
        if (definition == null) {
            warn("[PM/PUBLISH@" + serverName() + "] NO DEFINITION for type=" + packet.getClass().getName()
                    + " channel=" + renderChannel(target));
            return;
        }

        FriendlyByteOutputStream out = new FriendlyByteOutputStream();
        out.writeLong(definition.getRegistryId());
        packet.writeData(out);

        // Debug (not always)
        dbg("[PM/PUBLISH@" + serverName() + "] id=" + definition.getRegistryId()
                + " type=" + packet.getClass().getSimpleName()
                + " channel=" + renderChannel(target));

        // Extra detail for homes (debug-only)
        if (packet instanceof HomeTeleportRequestPacket h) {
            dbg("[PM/PUBLISH@" + serverName() + "/HOME]"
                    + " requestId=" + h.getRequestId()
                    + " player=" + h.getPlayerId()
                    + " home=" + h.getHomeName()
                    + " target=" + h.getTargetServer()
                    + " channel=" + renderChannel(target));
        }

        this.sender.sendPacket(target, out.toByteArray());
    }

    public void sendPacket(Packet packet) {
        sendPacket(PacketChannels.GLOBAL, packet);
    }

    public <T extends Packet> void subscribe(Class<T> packetClass, PacketSubscriber<T> subscriber) {
        @SuppressWarnings("unchecked")
        PacketSubscriptionQueue<T> queue =
                (PacketSubscriptionQueue<T>) this.subscriptions.computeIfAbsent(
                        packetClass, key -> new PacketSubscriptionQueue<>(packetClass));
        queue.subscribe(subscriber);
        dbg("[PM/SUB@" + serverName() + "] Subscribed " + packetClass.getSimpleName());
    }

    /* ====================== RECEIVE ====================== */

    @Override
    public void onReceive(PacketChannel channel, byte[] content) {
        FriendlyByteInputStream in = new FriendlyByteInputStream(content);
        long registryId = in.readLong();

        PacketDefinition<?> definition = this.packetRegistry.getDefinition(registryId);

        // Debug (not always)
        dbg("[PM/RECV@" + serverName() + "] registryId=" + registryId
                + " type=" + (definition != null ? definition.getPacketClass().getSimpleName() : "unknown")
                + " channel=" + renderChannel(channel));

        if (definition == null) {
            warn("[PM/RECV@" + serverName() + "] Unknown registry id: " + registryId);
            return;
        }

        Packet packet = definition.getProvider().createPacket();
        packet.readData(in);

        // Extra detail for homes (debug-only)
        if (packet instanceof HomeTeleportRequestPacket h) {
            dbg("[PM/RECV@" + serverName() + "/HOME]"
                    + " requestId=" + h.getRequestId()
                    + " player=" + h.getPlayerId()
                    + " home=" + h.getHomeName()
                    + " target=" + h.getTargetServer()
                    + " channel=" + renderChannel(channel));
        }

        dispatch(channel, packet);
    }

    /* ====================== DISPATCH ====================== */

    private <T extends Packet> void dispatch(PacketChannel channel, T packet) {
        @SuppressWarnings("unchecked")
        PacketSubscriptionQueue<T> queue =
                (PacketSubscriptionQueue<T>) this.subscriptions.get(packet.getClass());

        if (queue == null) {
            warn("[PM/DISPATCH@" + serverName() + "] No subscribers for " + packet.getClass().getName()
                    + " channel=" + renderChannel(channel));
            return;
        }

        queue.dispatch(channel, packet);
    }

    /* ====================== UTIL ====================== */

    private String renderChannel(PacketChannel ch) {
        try {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (String id : ch) {
                if (!first) sb.append(',');
                sb.append(id);
                first = false;
            }
            return sb.append(']').toString();
        } catch (Throwable t) {
            return ch.toString();
        }
    }

    private String serverName() {
        try {
            return plugin.getConfigService().serverName();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private void warn(String msg) { plugin.getLogger().warning(msg); }
    private void info(String msg) { plugin.getLogger().info(msg); } // kept for rare always-on logs if needed

    /* ---- debug helpers ---- */
    private boolean isDebug() {
        try { return plugin.getConfig().getBoolean("debug", false); } catch (Throwable t) { return false; }
    }
    private void dbg(String msg) { if (isDebug()) plugin.getLogger().info(msg); }

    public boolean isInitialized() { return initialized; }

    public void close() {
        initialized = false;
        sender.close();
        dbg("[PM/CLOSE@" + serverName() + "] PacketManager closed.");
    }
}
