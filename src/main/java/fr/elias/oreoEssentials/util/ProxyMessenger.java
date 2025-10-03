// src/main/java/fr/elias/oreoEssentials/util/ProxyMessenger.java
package fr.elias.oreoEssentials.util;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ProxyMessenger implements PluginMessageListener {

    private static final String BUNGEE_CHANNEL = "BungeeCord"; // works on Bungee & Velocity (compat)
    private final Plugin plugin;
    private final List<String> cachedServers = new CopyOnWriteArrayList<>();

    public ProxyMessenger(Plugin plugin) {
        this.plugin = plugin;
        // Register channels
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, BUNGEE_CHANNEL, this);
    }

    /** Ask proxy for server list (updates cache on response). */
    public void requestServers() {
        Player any = getAnyPlayer();
        if (any == null) return; // need a player context to send plugin message
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("GetServers");
            any.sendPluginMessage(plugin, BUNGEE_CHANNEL, b.toByteArray());
        } catch (Exception ignored) {}
    }

    /** Connect a player to another server. */
    public void connect(Player player, String serverName) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            player.sendPluginMessage(plugin, BUNGEE_CHANNEL, b.toByteArray());
        } catch (Exception ignored) {}
    }

    /** Current cached server list (may be empty until first response). */
    public List<String> getCachedServers() {
        return new ArrayList<>(cachedServers);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!BUNGEE_CHANNEL.equals(channel)) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String sub = in.readUTF();
            if ("GetServers".equalsIgnoreCase(sub)) {
                String csv = in.readUTF();
                List<String> list = new ArrayList<>();
                for (String s : csv.split(", ")) {
                    if (!s.isEmpty()) list.add(s);
                }
                cachedServers.clear();
                cachedServers.addAll(list);
            }
        } catch (Exception ignored) {}
    }

    private Player getAnyPlayer() {
        for (Player p : Bukkit.getOnlinePlayers()) return p;
        return null;
    }
}
