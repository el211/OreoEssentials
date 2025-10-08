// File: src/main/java/fr/elias/oreoEssentials/config/CrossServerSettings.java
package fr.elias.oreoEssentials.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class CrossServerSettings {
    private final boolean homes;
    private final boolean warps;
    private final boolean spawn;
    private final boolean economy;

    private CrossServerSettings(boolean homes, boolean warps, boolean spawn, boolean economy) {
        this.homes = homes;
        this.warps = warps;
        this.spawn = spawn;
        this.economy = economy;
    }

    public static CrossServerSettings load(JavaPlugin plugin) {
        FileConfiguration cfg = plugin.getConfig();
        boolean homes   = cfg.getBoolean("crossserverhomes",   true);
        boolean warps   = cfg.getBoolean("crossserverwarps",   true);
        boolean spawn   = cfg.getBoolean("crossserverspawn",   true);
        boolean economy = cfg.getBoolean("crossservereconomy", true);

        plugin.getLogger().info("[CROSS] homes=" + homes + " warps=" + warps + " spawn=" + spawn + " economy=" + economy);
        return new CrossServerSettings(homes, warps, spawn, economy);
    }

    public boolean homes()   { return homes; }
    public boolean warps()   { return warps; }
    public boolean spawn()   { return spawn; }
    public boolean economy() { return economy; }
}
