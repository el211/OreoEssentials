package fr.elias.oreoEssentials.rtp;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public final class RtpConfig {
    private final OreoEssentials plugin;
    private File file;
    private FileConfiguration cfg;

    public RtpConfig(OreoEssentials plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        try {
            if (file == null) file = new File(plugin.getDataFolder(), "rtp.yml");
            if (!file.exists()) plugin.saveResource("rtp.yml", false);
            cfg = YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            plugin.getLogger().warning("[RTP] Failed to load rtp.yml: " + e.getMessage());
            cfg = new YamlConfiguration();
        }
    }

    public int attempts() { return cfg.getInt("attempts", 30); }
    public int minY() { return cfg.getInt("min-y", 50); }
    public int maxY() { return cfg.getInt("max-y", 200); }

    public Set<String> allowedWorlds() {
        List<String> list = cfg.getStringList("allowed-worlds");
        return new HashSet<>(list == null ? Collections.emptyList() : list);
    }

    public Set<String> unsafeBlocks() {
        List<String> list = cfg.getStringList("unsafe-blocks");
        return new HashSet<>(list == null ? Collections.emptyList() : list);
    }

    /** Returns the best radius for a set of permission-tier keys. */
    public int bestRadiusFor(java.util.function.Predicate<String> hasTierPerm) {
        int best = cfg.getInt("default", 200);
        for (String key : cfg.getKeys(false)) {
            if ("default".equalsIgnoreCase(key)
                    || "attempts".equalsIgnoreCase(key)
                    || "unsafe-blocks".equalsIgnoreCase(key)
                    || "allowed-worlds".equalsIgnoreCase(key)
                    || "min-y".equalsIgnoreCase(key)
                    || "max-y".equalsIgnoreCase(key)) continue;
            int val = cfg.getInt(key, -1);
            if (val > 0 && hasTierPerm.test(key)) {
                if (val > best) best = val;
            }
        }
        return best;
    }
}
