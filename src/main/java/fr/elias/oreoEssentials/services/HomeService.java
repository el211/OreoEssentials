// File: src/main/java/fr/elias/oreoEssentials/services/HomeService.java
package fr.elias.oreoEssentials.services;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public class HomeService {
    private final StorageApi storage;
    private final ConfigService config;

    public HomeService(StorageApi storage, ConfigService config) {
        this.storage = storage;
        this.config = config;
    }

    public boolean setHome(Player player, String name, Location loc) {
        String n = name.toLowerCase();
        Set<String> existing = homes(player.getUniqueId());
        int max = config.getMaxHomesFor(player);
        if (!existing.contains(n) && existing.size() >= max) return false;
        return storage.setHome(player.getUniqueId(), n, loc);
    }

    public boolean delHome(UUID uuid, String name) { return storage.delHome(uuid, name); }
    public Location getHome(UUID uuid, String name) { return storage.getHome(uuid, name); }
    public Set<String> homes(UUID uuid) { return storage.homes(uuid); }
}
