package fr.elias.oreoEssentials.chat;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class CustomConfig {
    private final File file;
    private FileConfiguration config;

    public CustomConfig(OreoEssentials plugin, String fileName) {
        file = new File(plugin.getDataFolder(), fileName);
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                if (plugin.getResource(fileName) != null) {
                    plugin.saveResource(fileName, false);
                } else {
                    file.createNewFile();
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Error creating " + fileName + ": " + e.getMessage());
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration getCustomConfig() { return config; }

    public void saveCustomConfig() {
        try { config.save(file); }
        catch (IOException e) { OreoEssentials.get().getLogger().severe("Failed to save " + file.getName() + ": " + e.getMessage()); }
    }

    public void reloadCustomConfig() { config = YamlConfiguration.loadConfiguration(file); }
}
