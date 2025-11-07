package fr.elias.oreoEssentials.chat.channel;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.ChatSyncManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChannelEventListener implements Listener {
    private final ChannelManager channels;
    private final ChannelsConfig cfg;
    private final ChatSyncManager sync; // may be null

    public ChannelEventListener(ChannelManager channels, ChannelsConfig cfg, ChatSyncManager sync) {
        this.channels = channels;
        this.cfg = cfg;
        this.sync = sync;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        channels.handleAutoJoin(p);

        String msg = ChatColor.stripColor(e.getJoinMessage() == null ? (p.getName() + " joined.") : e.getJoinMessage());
        routeAndSend("player_join: " + msg);
        e.setJoinMessage(null); // suppress vanilla to avoid double posting
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        String msg = ChatColor.stripColor(e.getQuitMessage() == null ? (e.getPlayer().getName() + " left.") : e.getQuitMessage());
        routeAndSend("player_leave: " + msg);
        e.setQuitMessage(null);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        String msg = ChatColor.stripColor(e.getDeathMessage());
        routeAndSend("player_death: " + msg);
        e.setDeathMessage(null);
    }

    /** Route a system/meta message into the first matching channel by regex, else fall back to 'events' if present. */
    private void routeAndSend(String raw) {
        String chosen = null;
        for (ChatChannel ch : channels.all()) {
            if (ch.matches(raw)) { chosen = ch.getName().toLowerCase(); break; }
        }
        if (chosen == null && channels.get("events") != null) chosen = "events";
        if (chosen == null) return;

        String out = channels.get(chosen).getFormat()
                .replace("%channel%", channels.get(chosen).getDisplayName())
                .replace("%message%", raw);
        out = channels.colorize(out);

        // local recipients
        var recips = channels.recipientsFor(chosen);
        for (Player r : recips) r.sendMessage(out);

        // cross-server?
        ChatChannel ch = channels.get(chosen);
        if (ch != null && ch.isCrossServer() && sync != null) {
            try {
                // Add a new method on your ChatSyncManager:
                // publishChannelSystem(String server, String channel, String message)
                sync.publishChannelSystem(Bukkit.getServer().getName(), chosen, ChatColor.stripColor(out));
            } catch (Throwable t) {
                OreoEssentials.get().getLogger().warning("[Channels] Failed to publish system message: " + t.getMessage());
            }
        }
    }
}
