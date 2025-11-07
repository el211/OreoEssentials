package fr.elias.oreoEssentials.chat.channel;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PlayerChannelStore {
    private final File file;
    private YamlConfiguration data;

    public PlayerChannelStore(OreoEssentials plugin) {
        this.file = new File(plugin.getDataFolder(), "player_channels.yml");
        if (!file.exists()) {
            try { file.getParentFile().mkdirs(); file.createNewFile(); } catch (IOException ignored) {}
        }
        reload();
    }

    public synchronized void reload() {
        data = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void save() {
        try { data.save(file); } catch (IOException e) { OreoEssentials.get().getLogger().warning("Failed to save player_channels.yml: " + e.getMessage()); }
    }

    public synchronized Set<String> getJoined(UUID uuid) {
        List<String> list = data.getStringList(uuid.toString() + ".joined");
        return new HashSet<>(list);
    }

    public synchronized void setJoined(UUID uuid, Set<String> joined) {
        data.set(uuid.toString() + ".joined", new ArrayList<>(joined));
        save();
    }

    public synchronized String getSpeak(UUID uuid) {
        return data.getString(uuid.toString() + ".speak", null);
    }

    public synchronized void setSpeak(UUID uuid, String channel) {
        data.set(uuid.toString() + ".speak", channel);
        save();
    }
}
