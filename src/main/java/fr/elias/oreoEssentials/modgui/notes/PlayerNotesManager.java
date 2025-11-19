// package: fr.elias.oreoEssentials.modgui.notes
package fr.elias.oreoEssentials.modgui.notes;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PlayerNotesManager {

    private final OreoEssentials plugin;
    private final File file;
    private final FileConfiguration cfg;

    public PlayerNotesManager(OreoEssentials plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "notes.yml");
        if (!file.exists()) {
            try { file.getParentFile().mkdirs(); file.createNewFile(); } catch (Exception ignored) {}
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public List<String> getNotes(UUID player) {
        return new ArrayList<>(cfg.getStringList("players." + player.toString()));
    }

    public void addNote(UUID target, String staffName, String text) {
        List<String> list = getNotes(target);
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        list.add(ts + " | " + staffName + " | " + text);
        cfg.set("players." + target.toString(), list);
        save();
    }

    private void save() {
        try { cfg.save(file); } catch (Exception e) {
            plugin.getLogger().warning("Failed to save notes.yml: " + e.getMessage());
        }
    }
}
