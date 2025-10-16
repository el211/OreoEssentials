// File: src/main/java/fr/elias/oreoEssentials/services/YamlStorage.java
package fr.elias.oreoEssentials.services;

import fr.elias.oreoEssentials.util.LocUtil;
import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class YamlStorage implements StorageApi {
    private final Plugin plugin;
    private final File dataDir;
    private final File warpsFile;
    private final File spawnFile;
    private YamlConfiguration warps;
    private YamlConfiguration spawn;
    private final ConcurrentMap<UUID, YamlConfiguration> playerCache = new ConcurrentHashMap<>();

    public YamlStorage(Plugin plugin) {
        this.plugin = plugin;
        this.dataDir = new File(plugin.getDataFolder(), "data/playerdata");
        this.warpsFile = new File(plugin.getDataFolder(), "data/warps.yml");
        this.spawnFile = new File(plugin.getDataFolder(), "data/spawn.yml");
        init();
    }

    private void init() {
        try {
            if (!dataDir.exists()) dataDir.mkdirs();
            if (!warpsFile.getParentFile().exists()) warpsFile.getParentFile().mkdirs();
            if (!warpsFile.exists()) warpsFile.createNewFile();
            if (!spawnFile.exists()) spawnFile.createNewFile();
            this.warps = new YamlConfiguration();
            this.warps.load(warpsFile);
            this.spawn = new YamlConfiguration();
            this.spawn.load(spawnFile);
        } catch (IOException | InvalidConfigurationException e) {
            throw new RuntimeException("Failed to init YAML storage", e);
        }
    }

    private YamlConfiguration player(UUID uuid) {
        return playerCache.computeIfAbsent(uuid, id -> {
            File f = new File(dataDir, id + ".yml");
            YamlConfiguration y = new YamlConfiguration();
            try {
                if (!f.exists()) f.createNewFile();
                y.load(f);
            } catch (IOException | InvalidConfigurationException e) {
                throw new RuntimeException(e);
            }
            return y;
        });
    }

    @Override public void setSpawn(Location loc) {
        var sec = spawn.getConfigurationSection("spawn");
        if (sec == null) sec = spawn.createSection("spawn");
        LocUtil.write(sec, loc);
        saveSpawn();
    }

    @Override public Location getSpawn() {
        return LocUtil.read(spawn.getConfigurationSection("spawn"));
    }

    @Override public void setWarp(String name, Location loc) {
        String key = name.toLowerCase();
        var sec = warps.getConfigurationSection(key);
        if (sec == null) sec = warps.createSection(key);
        LocUtil.write(sec, loc);
        saveWarps();
    }

    @Override public boolean delWarp(String name) {
        String key = name.toLowerCase();
        if (warps.contains(key)) {
            warps.set(key, null);
            saveWarps();
            return true;
        }
        return false;
    }

    @Override public Location getWarp(String name) {
        return LocUtil.read(warps.getConfigurationSection(name.toLowerCase()));
    }

    @Override public Set<String> listWarps() {
        return warps.getKeys(false).stream().sorted().collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Override public boolean setHome(UUID uuid, String name, Location loc) {
        String key = "homes."+name.toLowerCase();
        var y = player(uuid);
        var sec = y.getConfigurationSection(key);
        if (sec == null) sec = y.createSection(key);
        LocUtil.write(sec, loc);
        savePlayer(uuid);
        return true;
    }

    @Override public boolean delHome(UUID uuid, String name) {
        String key = "homes."+name.toLowerCase();
        var y = player(uuid);
        if (y.contains(key)) {
            y.set(key, null);
            savePlayer(uuid);
            return true;
        }
        return false;
    }

    @Override public Location getHome(UUID uuid, String name) {
        return LocUtil.read(player(uuid).getConfigurationSection("homes."+name.toLowerCase()));
    }

    @Override public Set<String> homes(UUID uuid) {
        var y = player(uuid);
        var sec = y.getConfigurationSection("homes");
        if (sec == null) return java.util.Set.of();
        return sec.getKeys(false).stream().sorted().collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Override public void setLast(UUID uuid, Location loc) {
        var y = player(uuid);
        var sec = y.getConfigurationSection("lastLocation");
        if (sec == null) sec = y.createSection("lastLocation");
        LocUtil.write(sec, loc);
        savePlayer(uuid);
    }

    @Override public Location getLast(UUID uuid) {
        return LocUtil.read(player(uuid).getConfigurationSection("lastLocation"));
    }

    private void savePlayer(UUID uuid) {
        File f = new File(dataDir, uuid + ".yml");
        try { player(uuid).save(f); } catch (IOException e) { plugin.getLogger().warning("Failed saving " + f + ": " + e.getMessage()); }
    }
    private void saveWarps() { try { warps.save(warpsFile); } catch (IOException e) { plugin.getLogger().warning("Failed saving warps: " + e.getMessage()); } }
    private void saveSpawn() { try { spawn.save(spawnFile); } catch (IOException e) { plugin.getLogger().warning("Failed saving spawn: " + e.getMessage()); } }

    @Override public void flush() {
        playerCache.keySet().forEach(this::savePlayer);
        saveWarps();
        saveSpawn();
    }
    @Override
    public Map<String, HomeService.StoredHome> listHomes(UUID owner) {
        Map<String, HomeService.StoredHome> out = new LinkedHashMap<>();

        var yml = player(owner);
        var homesSec = yml.getConfigurationSection("homes");
        if (homesSec == null) return out;

        for (String name : homesSec.getKeys(false)) {
            var sec = homesSec.getConfigurationSection(name);
            org.bukkit.Location loc = fr.elias.oreoEssentials.util.LocUtil.read(sec);
            if (loc == null) continue;

            String world = (loc.getWorld() != null ? loc.getWorld().getName() : "world");
            double x = loc.getX();
            double yy = loc.getY();
            double z = loc.getZ();
            String server = org.bukkit.Bukkit.getServer().getName();

            out.put(name.toLowerCase(java.util.Locale.ROOT),
                    new HomeService.StoredHome(world, x, yy, z, server));
        }
        return out;
    }


    @Override public void close() { /* no-op */ }
}
