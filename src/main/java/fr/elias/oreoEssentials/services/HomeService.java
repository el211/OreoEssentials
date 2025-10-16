// src/main/java/fr/elias/oreoEssentials/services/HomeService.java
package fr.elias.oreoEssentials.services;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class HomeService {
    private final StorageApi storage;
    private final ConfigService config;
    private final HomeDirectory directory;
    private final String localServer; // <- single source

    public HomeService(StorageApi storage, ConfigService config, HomeDirectory directory) {
        this.storage = storage;
        this.config = config;
        this.directory = directory;
        this.localServer = config.serverName(); // <-- IMPORTANT change
    }

    public boolean setHome(Player player, String name, Location loc) {
        String n = name.toLowerCase();
        Set<String> existing = homes(player.getUniqueId());
        int max = config.getMaxHomesFor(player);
        if (!existing.contains(n) && existing.size() >= max) return false;

        boolean ok = storage.setHome(player.getUniqueId(), n, loc);
        if (ok && directory != null) {
            // record ownership with the *same* server name source
            directory.setHomeServer(player.getUniqueId(), n, localServer);
        }
        return ok;
    }

    public boolean delHome(UUID uuid, String name) {
        boolean ok = storage.delHome(uuid, name);
        if (ok && directory != null) directory.deleteHome(uuid, name);
        return ok;
    }

    public Location getHome(UUID uuid, String name) { return storage.getHome(uuid, name); }
    public Set<String> homes(UUID uuid) { return storage.homes(uuid); }

    /** Returns server that owns home, or local if unknown. */
    public String homeServer(UUID uuid, String name) {
        if (directory == null) return localServer;
        String s = directory.getHomeServer(uuid, name);
        return s == null ? localServer : s;
    }
// inside HomeService.java

    public static final class StoredHome {
        private final String world;
        private final double x, y, z;
        private final String server;

        public StoredHome(String world, double x, double y, double z, String server) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.server = server;
        }
        public String getWorld()  { return world; }
        public double getX()      { return x; }
        public double getY()      { return y; }
        public double getZ()      { return z; }
        public String getServer() { return server; }
    }

    /**
     * Returns all homes for the given player, keyed by (lower-cased) home name.
     * This is the canonical method that /otherhomes and /otherhome use.
     */
    public Map<String, StoredHome> listHomes(java.util.UUID owner) {
        // delegate to storage (see next step)
        return storage.listHomes(owner);
    }

    public String localServer() { return localServer; }
}
