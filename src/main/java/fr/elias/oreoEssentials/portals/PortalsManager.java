package fr.elias.oreoEssentials.portals;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PortalsManager {

    public static class Portal {
        public final String name;
        public final World world;
        public final BoundingBox box;
        public final Location destination;
        public final boolean keepYawPitch;

        public Portal(String name, World world, BoundingBox box, Location destination, boolean keepYawPitch) {
            this.name = name;
            this.world = world;
            this.box = box;
            this.destination = destination;
            this.keepYawPitch = keepYawPitch;
        }

        public boolean contains(Location loc) {
            return loc != null
                    && loc.getWorld() != null
                    && loc.getWorld().equals(world)
                    && box.contains(loc.toVector());
        }
    }

    private final Plugin plugin;
    private final File file;
    private final FileConfiguration cfg;

    // runtime maps
    private final Map<String, Portal> portals = new ConcurrentHashMap<>(64);
    private final Map<UUID, Location> pos1 = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pos2 = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();

    // --- Config-backed options ---
    private final boolean enabled;
    private final long cooldownMs;
    private final String soundName;
    private final String particleName;
    private final int particleCount;
    private final boolean allowKeepYawPitch;

    public PortalsManager(Plugin plugin) {
        this.plugin = plugin;

        // read config (with sane defaults)
        FileConfiguration c = plugin.getConfig();
        this.enabled = c.getBoolean("portals.enabled", true);
        this.cooldownMs = c.getLong("portals.cooldown_ms", 1000L);
        this.soundName = c.getString("portals.sound", "ENTITY_ENDERMAN_TELEPORT");
        this.particleName = c.getString("portals.particle", "PORTAL");
        this.particleCount = c.getInt("portals.particle_count", 20);
        this.allowKeepYawPitch = c.getBoolean("portals.allow_keep_yaw_pitch", true);

        // data file
        this.file = new File(plugin.getDataFolder(), "portals.yml");
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create portals.yml: " + e.getMessage());
            }
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
        loadAll();
    }

    public Set<String> listNames() {
        return new TreeSet<>(portals.keySet());
    }

    public void setPos1(Player p, Location l) { pos1.put(p.getUniqueId(), l.clone()); }
    public void setPos2(Player p, Location l) { pos2.put(p.getUniqueId(), l.clone()); }

    public Location getPos1(Player p) { return pos1.get(p.getUniqueId()); }
    public Location getPos2(Player p) { return pos2.get(p.getUniqueId()); }

    public Portal get(String name) { return portals.get(name.toLowerCase(Locale.ROOT)); }

    public boolean create(String name, Player creator, Location dest, boolean keepYawPitch) {
        Location a = pos1.get(creator.getUniqueId());
        Location b = pos2.get(creator.getUniqueId());
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) return false;
        if (!a.getWorld().equals(b.getWorld())) return false;

        World w = a.getWorld();
        BoundingBox box = BoundingBox.of(a, b);

        String key = name.toLowerCase(Locale.ROOT);
        Portal portal = new Portal(name, w, box, dest.clone(), keepYawPitch);
        portals.put(key, portal);

        savePortal(portal);
        saveFile();
        return true;
    }

    public boolean remove(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!portals.containsKey(key)) return false;
        portals.remove(key);
        cfg.set("portals." + key, null);
        saveFile();
        return true;
    }

    public void loadAll() {
        portals.clear();
        ConfigurationSection root = cfg.getConfigurationSection("portals");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                String worldName = s.getString("world");
                World w = Bukkit.getWorld(worldName);
                if (w == null) continue;

                double x1 = s.getDouble("box.x1");
                double y1 = s.getDouble("box.y1");
                double z1 = s.getDouble("box.z1");
                double x2 = s.getDouble("box.x2");
                double y2 = s.getDouble("box.y2");
                double z2 = s.getDouble("box.z2");
                BoundingBox box = BoundingBox.of(new Location(w, x1, y1, z1), new Location(w, x2, y2, z2));

                String dw = s.getString("dest.world");
                World dworld = Bukkit.getWorld(dw);
                double dx = s.getDouble("dest.x");
                double dy = s.getDouble("dest.y");
                double dz = s.getDouble("dest.z");
                float yaw = (float) s.getDouble("dest.yaw", 0f);
                float pitch = (float) s.getDouble("dest.pitch", 0f);
                boolean keep = s.getBoolean("keepYawPitch", false);

                if (dworld == null) continue;
                Location dest = new Location(dworld, dx, dy, dz, yaw, pitch);

                portals.put(key.toLowerCase(Locale.ROOT), new Portal(s.getString("name", key), w, box, dest, keep));
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to load portal " + key + ": " + t.getMessage());
            }
        }
    }

    private void savePortal(Portal p) {
        String key = p.name.toLowerCase(Locale.ROOT);
        String base = "portals." + key + ".";
        cfg.set(base + "name", p.name);
        cfg.set(base + "world", p.world.getName());
        cfg.set(base + "box.x1", p.box.getMinX());
        cfg.set(base + "box.y1", p.box.getMinY());
        cfg.set(base + "box.z1", p.box.getMinZ());
        cfg.set(base + "box.x2", p.box.getMaxX());
        cfg.set(base + "box.y2", p.box.getMaxY());
        cfg.set(base + "box.z2", p.box.getMaxZ());
        cfg.set(base + "dest.world", p.destination.getWorld().getName());
        cfg.set(base + "dest.x", p.destination.getX());
        cfg.set(base + "dest.y", p.destination.getY());
        cfg.set(base + "dest.z", p.destination.getZ());
        cfg.set(base + "dest.yaw", p.destination.getYaw());
        cfg.set(base + "dest.pitch", p.destination.getPitch());
        cfg.set(base + "keepYawPitch", p.keepYawPitch);
    }

    private void saveFile() {
        try { cfg.save(file); }
        catch (IOException e) { plugin.getLogger().severe("Failed to save portals.yml: " + e.getMessage()); }
    }

    /** Teleport if inside any portal (simple O(n) scan, fast enough for dozens). */
    public void tickMove(Player p, Location to) {
        if (!enabled || to == null) return;

        long now = System.currentTimeMillis();
        Long cd = cooldown.get(p.getUniqueId());
        if (cd != null && now < cd) return; // cooldown

        for (Portal portal : portals.values()) {
            if (portal.contains(to)) {
                Location dest = portal.destination.clone();
                if (allowKeepYawPitch && portal.keepYawPitch) {
                    dest.setYaw(p.getLocation().getYaw());
                    dest.setPitch(p.getLocation().getPitch());
                }

                // FX (configurable)
                try {
                    if (soundName != null && !soundName.isEmpty()) {
                        p.getWorld().playSound(p.getLocation(), Sound.valueOf(soundName), 1f, 1f);
                    }
                    if (particleName != null && !particleName.isEmpty() && particleCount > 0) {
                        p.getWorld().spawnParticle(
                                Particle.valueOf(particleName),
                                p.getLocation(), particleCount,
                                0.25, 0.5, 0.25, 0.01
                        );
                    }
                } catch (IllegalArgumentException ignored) {
                    // bad enum names in config; ignore FX
                }

                // apply cooldown to avoid bounce or recursive portals
                cooldown.put(p.getUniqueId(), now + Math.max(0, cooldownMs));
                p.teleport(dest);
                return;
            }
        }
    }
}
