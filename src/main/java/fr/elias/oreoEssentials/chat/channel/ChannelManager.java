// File: src/main/java/fr/elias/oreoEssentials/chat/channel/ChannelManager.java
package fr.elias.oreoEssentials.chat.channel;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class ChannelManager {
    private final OreoEssentials plugin;
    private final ChannelsConfig config;
    private final PlayerChannelStore store;

    /** Local snapshot of channels (lowercased ids) */
    private Map<String, ChatChannel> channels;

    public ChannelManager(OreoEssentials plugin, ChannelsConfig cfg, PlayerChannelStore store) {
        this.plugin = plugin;
        this.config = cfg;
        this.store = store;
        this.channels = snapshotFromConfig(cfg);
    }

    /** Rebuild local snapshot after config reload */
    public void reload() {
        config.reload();
        this.channels = snapshotFromConfig(config);
    }

    private Map<String, ChatChannel> snapshotFromConfig(ChannelsConfig cfg) {
        Map<String, ChatChannel> map = new LinkedHashMap<>();
        for (String id : cfg.getAllChannelIds()) {
            ChatChannel ch = cfg.get(id);
            if (ch != null) {
                map.put(id.toLowerCase(Locale.ROOT), ch);
            }
        }
        return map;
    }

    public ChatChannel get(String name) {
        if (name == null) return null;
        return channels.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<ChatChannel> all() {
        return channels.values();
    }

    public void handleAutoJoin(Player p) {
        UUID id = p.getUniqueId();
        Set<String> joined = store.getJoined(id);

        // from file config "default_autojoin"
        Set<String> defaults = config.getDefaultAutoJoin();

        // from per-channel autojoin flag + permission oreoessentials.channel.autojoin.<name>
        for (ChatChannel ch : channels.values()) {
            boolean canAuto = ch.isDefaultAutoJoin() ||
                    p.hasPermission("oreoessentials.channel.autojoin." + ch.getName().toLowerCase(Locale.ROOT));
            if (canAuto) joined.add(ch.getName().toLowerCase(Locale.ROOT));
        }

        // include global defaults
        joined.addAll(defaults.stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet()));

        // keep only channels that exist and the player can actually read (join permission)
        joined = joined.stream()
                .filter(cn -> {
                    ChatChannel ch = get(cn);
                    if (ch == null) return false;
                    return p.hasPermission(ch.getPermissionJoin());
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));

        store.setJoined(id, joined);

        // default speak channel if none
        if (store.getSpeak(id) == null) {
            String local = config.getLocalDefaultChannel();
            ChatChannel ch = get(local);
            if (ch != null && joined.contains(local.toLowerCase(Locale.ROOT))) {
                store.setSpeak(id, local.toLowerCase(Locale.ROOT));
            } else {
                // pick any joined writable channel
                String fallback = joined.stream()
                        .filter(n -> {
                            ChatChannel c = get(n);
                            return c != null && !c.isReadOnly();
                        })
                        .findFirst()
                        .orElse(null);
                store.setSpeak(id, fallback);
            }
        }
    }

    public boolean join(Player p, String name) {
        ChatChannel ch = get(name);
        if (ch == null) return false;
        if (!p.hasPermission(ch.getPermissionJoin())) return false;

        Set<String> joined = store.getJoined(p.getUniqueId());
        boolean added = joined.add(ch.getName().toLowerCase(Locale.ROOT));
        store.setJoined(p.getUniqueId(), joined);
        return added;
    }

    public boolean leave(Player p, String name) {
        ChatChannel ch = get(name);
        if (ch == null) return false;
        if (!p.hasPermission(ch.getPermissionLeave())) return false;

        Set<String> joined = store.getJoined(p.getUniqueId());
        boolean removed = joined.remove(ch.getName().toLowerCase(Locale.ROOT));

        // if speaking channel removed, move to local/default
        if (removed) {
            if (Objects.equals(store.getSpeak(p.getUniqueId()), ch.getName().toLowerCase(Locale.ROOT))) {
                store.setSpeak(p.getUniqueId(), null);
                handleAutoJoin(p);
            }
            store.setJoined(p.getUniqueId(), joined);
        }
        return removed;
    }

    public boolean setSpeak(Player p, String name) {
        ChatChannel ch = get(name);
        if (ch == null) return false;
        if (ch.isReadOnly()) return false;
        if (!p.hasPermission(ch.getPermissionSpeak())) return false;

        Set<String> joined = store.getJoined(p.getUniqueId());
        if (!joined.contains(ch.getName().toLowerCase(Locale.ROOT))) return false;

        store.setSpeak(p.getUniqueId(), ch.getName().toLowerCase(Locale.ROOT));
        return true;
    }

    public String getSpeakChannel(Player p) {
        return store.getSpeak(p.getUniqueId());
    }

    public Set<Player> recipientsFor(String channelLower) {
        Set<Player> set = new LinkedHashSet<>();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            Set<String> joined = store.getJoined(pl.getUniqueId());
            if (joined.contains(channelLower)) set.add(pl);
        }
        return set;
    }

    /** Convenience used by GUI */
    public boolean isJoined(Player p, String name) {
        if (p == null || name == null) return false;
        Set<String> joined = store.getJoined(p.getUniqueId());
        return joined.contains(name.toLowerCase(Locale.ROOT));
    }

    /** Back-compat alias if old code calls setSpeakChannel(...) */
    public boolean setSpeakChannel(Player p, String name) {
        return setSpeak(p, name);
    }

    public String colorize(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
