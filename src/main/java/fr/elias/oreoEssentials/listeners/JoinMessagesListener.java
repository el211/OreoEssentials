package fr.elias.oreoEssentials.listeners;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public final class JoinMessagesListener implements Listener {
    private final Plugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.builder()
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    public JoinMessagesListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        // Spigot/Paper (String) — clears vanilla join line
        e.setJoinMessage(null);

        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("Join_messages.enable", false)) return;

        final Player p = e.getPlayer();
        final boolean firstJoin = !p.hasPlayedBefore();

        final boolean lookLikePlayer = c.getBoolean("Join_messages.look_like_player", false);
        final String playerNameFmt   = c.getString("Join_messages.player_name", "{name}");
        final String playerPrefixFmt = c.getString("Join_messages.player_prefix", "");
        final String delimiter       = c.getString("Join_messages.delimiter", " | ");

        String body = c.getString(
                firstJoin ? "Join_messages.first_join" : "Join_messages.rejoin_message",
                "{name} joined the game."
        );

        final String namePlain = p.getName();
        final String playerName = playerNameFmt.replace("{name}", namePlain);
        body = body.replace("{name}", namePlain);

        final String output = lookLikePlayer
                ? playerPrefixFmt + " " + playerName + " " + delimiter + " " + body
                : body;

        long delayTicks = Math.max(0L, c.getLong("Join_messages.join_message_delay", 0L) * 20L);

        Runnable send = () -> {
            String legacyMsg = legacy.serialize(mm.deserialize(output));
            Bukkit.getOnlinePlayers().forEach(pl -> pl.sendMessage(legacyMsg));
        };

        if (delayTicks > 0) Bukkit.getScheduler().runTaskLater(plugin, send, delayTicks);
        else Bukkit.getScheduler().runTask(plugin, send);
    }
}
