// File: src/main/java/fr/elias/oreoEssentials/rtp/RtpConfig.java
package fr.elias.oreoEssentials.rtp;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.function.Predicate;

public final class RtpConfig {
    private final OreoEssentials plugin;
    private File file;
    private volatile FileConfiguration cfg;

    // Master toggle (NEW)
    private volatile boolean enabled = true;

    // cached lookups
    private Set<String> allowedWorlds = Collections.emptySet();
    private Set<String> unsafeBlocks  = Collections.emptySet();

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

        // NEW: master toggle (defaults to true)
        enabled = cfg.getBoolean("enabled", true);

        // refresh caches
        List<String> aw = cfg.getStringList("allowed-worlds");
        allowedWorlds = new HashSet<>(aw == null ? Collections.emptyList() : aw);

        List<String> ub = cfg.getStringList("unsafe-blocks");
        unsafeBlocks = new HashSet<>(ub == null ? Collections.emptyList() : ub);
    }

    /** Persist ONLY the toggle so we don't rewrite the user's YAML layout. */
    public void save() {
        if (cfg == null || file == null) return;
        try {
            cfg.set("enabled", enabled);
            cfg.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("[RTP] Failed to save rtp.yml: " + e.getMessage());
        }
    }

    /* ----------------- Basic getters ----------------- */

    public boolean isEnabled() { return enabled; }                         // NEW
    public void setEnabled(boolean v) { this.enabled = v; }                // NEW
    public boolean toggleEnabled() { this.enabled = !this.enabled; return this.enabled; } // NEW

    public int attempts() { return cfg.getInt("attempts", 30); }
    public int minY()     { return cfg.getInt("min-y", 50); }
    public int maxY()     { return cfg.getInt("max-y", 200); }

    public Set<String> allowedWorlds() { return allowedWorlds; }
    public Set<String> unsafeBlocks()  { return unsafeBlocks; }

    /** True if world is allowed (empty list means all). */
    public boolean isWorldAllowed(World w) {
        return allowedWorlds.isEmpty() || allowedWorlds.contains(w.getName());
    }

    /**
     * Get the best radius for a player, honoring per-world overrides and tier permissions.
     * Falls back to global "default" when no world override applies.
     */
    public int radiusFor(Player p, Collection<String> tierPermissionKeys) {
        Predicate<String> hasTier;
        if (tierPermissionKeys == null || tierPermissionKeys.isEmpty()) {
            hasTier = p::hasPermission;
        } else {
            // In case you pass pre-known permission keys (e.g. "oreo.tier.vip", "oreo.tier.mvp")
            Set<String> keys = new HashSet<>(tierPermissionKeys);
            hasTier = p::hasPermission; // still rely on real permissions
            // (Keeping behavior consistent—if you wanted to strictly check only those keys, swap to:
            // hasTier = keys::contains;
            // But the original code suggests real permission checks.)
        }

        // Check per-world section first
        String worldKey = "worlds." + p.getWorld().getName();
        if (cfg.isConfigurationSection(worldKey)) {
            int worldDefault = cfg.getInt(worldKey + ".default", cfg.getInt("default", 200));
            int best = worldDefault;
            for (String key : cfg.getConfigurationSection(worldKey).getKeys(false)) {
                if ("default".equalsIgnoreCase(key)) continue;
                int val = cfg.getInt(worldKey + "." + key, -1);
                if (val > 0 && hasTier.test(key)) best = Math.max(best, val);
            }
            return best;
        }

        // Fallback: global section
        return bestRadiusFor(hasTier);
    }

    /** Backward-compatible global best-radius calculator (what you already had). */
    public int bestRadiusFor(Predicate<String> hasTierPerm) {
        int best = cfg.getInt("default", 200);
        for (String key : cfg.getKeys(false)) {
            if (isNonTierKey(key)) continue;
            int val = cfg.getInt(key, -1);
            if (val > 0 && hasTierPerm.test(key)) {
                if (val > best) best = val;
            }
        }
        return best;
    }

    private boolean isNonTierKey(String key) {
        return "enabled".equalsIgnoreCase(key)          // NEW
                || "default".equalsIgnoreCase(key)
                || "attempts".equalsIgnoreCase(key)
                || "unsafe-blocks".equalsIgnoreCase(key)
                || "allowed-worlds".equalsIgnoreCase(key)
                || "min-y".equalsIgnoreCase(key)
                || "max-y".equalsIgnoreCase(key)
                || "worlds".equalsIgnoreCase(key);
    }
}
