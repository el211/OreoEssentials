// File: src/main/java/fr/elias/oreoEssentials/chat/channel/ChannelsConfig.java
package fr.elias.oreoEssentials.chat.channel;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ChannelsConfig {
    private final OreoEssentials plugin;
    private final File file;
    private YamlConfiguration cfg;

    /** Cached channels keyed by lowercased id */
    private Map<String, ChatChannel> channels = new LinkedHashMap<>();

    public ChannelsConfig(OreoEssentials plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "channels.yml");
        if (!file.exists()) {
            plugin.saveResource("channels.yml", false);
        }
        reload();
    }

    /** Reload YAML and rebuild the channels cache. */
    public void reload() {
        this.cfg = YamlConfiguration.loadConfiguration(file);
        this.channels = buildChannelsFromConfig();
    }

    /** Build channels from YAML into a map keyed by lowercased id. */
    private Map<String, ChatChannel> buildChannelsFromConfig() {
        Map<String, ChatChannel> map = new LinkedHashMap<>();
        ConfigurationSection chSec = cfg.getConfigurationSection("channels");
        if (chSec == null) return map;

        for (String key : chSec.getKeys(false)) {
            ConfigurationSection c = chSec.getConfigurationSection(key);
            if (c == null) continue;

            String display   = c.getString("display", key);
            boolean readOnly = c.getBoolean("read_only", false);
            boolean cross    = c.getBoolean("cross_server", false);
            boolean autoJoin = c.getBoolean("autojoin_default", false);

            String permJoin  = c.getString("permissions.join",
                    "oreoessentials.channel.join." + key.toLowerCase(Locale.ROOT));
            String permLeave = c.getString("permissions.leave",
                    "oreoessentials.channel.leave." + key.toLowerCase(Locale.ROOT));
            String permSpeak = c.getString("permissions.speak",
                    "oreoessentials.channel." + key.toLowerCase(Locale.ROOT));

            List<String> regexes = c.getStringList("route.regex");
            List<Pattern> patterns = regexes.stream()
                    .map(r -> Pattern.compile(r, Pattern.CASE_INSENSITIVE))
                    .collect(Collectors.toList());

            String fmt = c.getString("route.format", "&7[%channel%] %message%");

            map.put(key.toLowerCase(Locale.ROOT), new ChatChannel(
                    key, display, readOnly, cross, autoJoin,
                    permJoin, permLeave, permSpeak, patterns, fmt
            ));
        }
        return map;
    }

    /* ----------------- Public API used by GUI/Manager ----------------- */

    /** Get a channel by id (case-insensitive). */
    public ChatChannel get(String id) {
        if (id == null) return null;
        return channels.get(id.toLowerCase(Locale.ROOT));
    }

    /** Get all channel ids (lowercased, as stored). */
    public List<String> getAllChannelIds() {
        return new ArrayList<>(channels.keySet());
    }

    /** For auto-join list from root config. */
    public Set<String> getDefaultAutoJoin() {
        List<String> list = cfg.getStringList("default_autojoin");
        return list == null ? new HashSet<>() : new HashSet<>(list);
    }

    public String getLocalDefaultChannel() {
        return cfg.getString("defaults.local_speak_channel", "local");
    }

    public String getGlobalChannel() {
        return cfg.getString("defaults.global_channel", "global");
    }

    public String getPrefix() {
        return cfg.getString("messages.prefix", "&8[&bChannels&8]&r ");
    }

    public String msg(String path, Map<String, String> repl) {
        String base = cfg.getString("messages." + path, "");
        if (repl != null) {
            for (var e : repl.entrySet()) {
                base = base.replace("%" + e.getKey() + "%", e.getValue());
            }
        }
        return base;
    }
}
