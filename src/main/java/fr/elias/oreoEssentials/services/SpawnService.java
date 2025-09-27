// File: src/main/java/fr/elias/oreoEssentials/services/SpawnService.java
package fr.elias.oreoEssentials.services;

import org.bukkit.Location;

public class SpawnService {
    private final StorageApi storage;
    public SpawnService(StorageApi storage) { this.storage = storage; }
    public void setSpawn(Location loc) { storage.setSpawn(loc); }
    public Location getSpawn() { return storage.getSpawn(); }
}
