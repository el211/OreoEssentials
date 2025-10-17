package fr.elias.oreoEssentials.aliases;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AliasService {

    /* ------------------------------ Model ------------------------------ */

    public static final class AliasDef {
        public String name;
        public boolean enabled = true;
        public RunAs runAs = RunAs.PLAYER;
        public int cooldownSeconds = 0;
        public List<String> commands = new ArrayList<>();
    }

    public enum RunAs { PLAYER, CONSOLE }

    /* ------------------------------ State ------------------------------ */

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration cfg;

    // alias name -> definition
    private final Map<String, AliasDef> aliases = new ConcurrentHashMap<>();
    // cooldown key: alias|playerUUID -> last-used millis
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    /* --------------------------- Lifecycle ----------------------------- */

    public AliasService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "aliases.yml");
    }

    /** Loads aliases from aliases.yml (creates the file from resources if missing). */
    public void load() {
        if (!file.exists()) {
            try { plugin.saveResource("aliases.yml", false); } catch (Throwable ignored) {}
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
        aliases.clear();

        ConfigurationSection root = cfg.getConfigurationSection("aliases");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            ConfigurationSection a = root.getConfigurationSection(key);
            if (a == null) continue;

            AliasDef def = new AliasDef();
            def.name = key.toLowerCase(Locale.ROOT);
            def.enabled = a.getBoolean("enabled", true);
            def.runAs = parseRunAs(a.getString("run-as", "PLAYER"));
            def.cooldownSeconds = a.getInt("cooldown-seconds", 0);
            def.commands = a.getStringList("commands");
            aliases.put(def.name, def);
        }
    }

    /** Saves the current in-memory aliases to aliases.yml. */
    public void save() {
        YamlConfiguration out = new YamlConfiguration();
        ConfigurationSection root = out.createSection("aliases");

        for (AliasDef def : aliases.values()) {
            ConfigurationSection a = root.createSection(def.name);
            a.set("enabled", def.enabled);
            a.set("run-as", def.runAs.name());
            a.set("cooldown-seconds", def.cooldownSeconds);
            a.set("commands", def.commands);
        }

        try {
            out.save(file);
            this.cfg = out;
        } catch (Exception e) {
            plugin.getLogger().warning("[Aliases] Failed to save aliases.yml: " + e.getMessage());
        }
    }

    /** Registers runtime Bukkit commands for all enabled aliases. Safe to call after reload(). */
    public void applyRuntimeRegistration() {
        // Unregister previous dynamic commands to avoid duplicates
        DynamicAliasRegistry.unregisterAll(plugin);

        int count = 0;
        for (AliasDef def : aliases.values()) {
            if (!def.enabled) continue;
            // bind executor per-alias so it can read & execute live config
            DynamicAliasRegistry.register(
                    plugin,
                    def.name,
                    new DynamicAliasExecutor(plugin, this, def.name),
                    "Oreo alias"
            );
            count++;
        }
        plugin.getLogger().info("[Aliases] Registered " + count + " alias command(s).");
    }

    /** Cleanly unregisters all runtime aliases. Call from onDisable(). */
    public void shutdown() {
        try {
            DynamicAliasRegistry.unregisterAll(plugin);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Aliases] Unregister failed: " + t.getMessage());
        }
    }

    /* ----------------------------- API -------------------------------- */

    public Map<String, AliasDef> all() {
        return Collections.unmodifiableMap(aliases);
    }

    public boolean exists(String name) {
        return aliases.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public AliasDef get(String name) {
        return aliases.get(name.toLowerCase(Locale.ROOT));
    }

    public void put(AliasDef def) {
        if (def == null || def.name == null) return;
        aliases.put(def.name.toLowerCase(Locale.ROOT), def);
    }

    public void remove(String name) {
        if (name == null) return;
        aliases.remove(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Cooldown gate. Returns true if the alias is allowed to run now and records usage;
     * false if the caller must wait longer.
     */
    public boolean checkAndTouchCooldown(String alias, UUID player, int seconds) {
        if (seconds <= 0 || player == null) return true;
        final String key = alias.toLowerCase(Locale.ROOT) + "|" + player;
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(key);
        if (last != null && (now - last) < seconds * 1000L) return false;
        cooldowns.put(key, now);
        return true;
    }

    /* --------------------------- Helpers ------------------------------ */

    private RunAs parseRunAs(String s) {
        try { return RunAs.valueOf(s.toUpperCase(Locale.ROOT)); }
        catch (Throwable ignored) { return RunAs.PLAYER; }
    }
}
