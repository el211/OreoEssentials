package fr.elias.oreoEssentials.rabbitmq.sender;


import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.event.IncomingPacketListener;
import fr.elias.oreoEssentials.rabbitmq.packet.event.PacketSender;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RabbitMQSender implements PacketSender {

    private final String connectionString;

    private Connection connection;
    private Channel channel;

    public RabbitMQSender(String connectionString) {
        this.connectionString = connectionString;
    }

    private final List<IncomingPacketListener> listeners = new ArrayList<>();
    private final List<String> queues = new ArrayList<>();

    @Override
    public void sendPacket(PacketChannel channel, byte[] content) {
        try {
            for (String id : channel) {
                this.channel.basicPublish("", id, null, content);
            }

            System.out.println("[OreoEssentials] ✅ Sent RabbitMQ message");
        } catch (IOException e) {
            System.err.println("[OreoEssentials] ❌ Failed to send RabbitMQ message.");
            e.printStackTrace();
        }
    }

    @Override
    public void registerChannel(PacketChannel channel) {
        if (this.channel == null || this.connection == null || !this.channel.isOpen()) {
            System.err.println("[OreoEssentials] ❌ Error: RabbitMQ channel is not connected!");
            reconnect();  // ✅ Correct method call to reconnect
            return;
        }

        try {
            for (String id : channel) {
                if (queues.contains(id)) {
                    continue;
                }

                queues.add(id);
                this.channel.queueDeclare(id, true, false, false, null);

                this.channel.basicConsume(id, false, (consumerTag, delivery) -> {
                    byte[] content = delivery.getBody();
                    handleIncomingPacket(id, content);
                }, consumerTag -> {});
            }
        } catch (IOException e) {
            System.err.println("[OreoEssentials] ❌ Failed to receive messages!");
            e.printStackTrace();
            reconnect();
        }
    }

    @Override
    public void registerListener(IncomingPacketListener listener) {
        listeners.add(listener);
    }

    // ✅ Ensure `reconnect()` exists
    private void reconnect() {
        System.err.println("[OreoEssentials] 🔄 Attempting to reconnect to RabbitMQ...");
        close();
        boolean success = connect();
        if (success) {
            System.out.println("[OreoEssentials] ✅ Successfully reconnected to RabbitMQ!");
        } else {
            System.err.println("[OreoEssentials] ❌ Reconnection failed!");
        }
    }

    public boolean connect() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setUri(connectionString);
            factory.setAutomaticRecoveryEnabled(true);
            factory.setNetworkRecoveryInterval(5000);

            connection = factory.newConnection();
            channel = connection.createChannel();

            for (String queue : queues) {
                channel.queueDeclare(queue, true, false, false, null);
            }
            System.out.println("[OreoEssentials] ✅ Connected to RabbitMQ successfully!");
            return true;
        } catch (Exception e) {
            System.err.println("[OreoEssentials] ❌ Failed to connect to RabbitMQ!");
            e.printStackTrace();
            return false;
        }
    }

    public void close() {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
            System.out.println("[OreoEssentials] ✅ RabbitMQ connection closed.");
        } catch (Exception e) {
            System.err.println("[OreoEssentials] ❌ Error closing RabbitMQ connection!");
            e.printStackTrace();
        }
    }

    private void handleIncomingPacket(String id, byte[] content) {
        try {
            PacketChannel channel = PacketChannel.individual(id);

            for (IncomingPacketListener listener : listeners) {
                listener.onReceive(channel, content);
            }
        } catch (Exception e) {
            System.err.println("[OreoEssentials] ❌ Error while handling incoming packet!");
            e.printStackTrace();
        }
    }
}
