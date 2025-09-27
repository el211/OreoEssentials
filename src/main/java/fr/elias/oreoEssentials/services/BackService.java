// File: src/main/java/fr/elias/oreoEssentials/services/BackService.java
package fr.elias.oreoEssentials.services;

import org.bukkit.Location;

import java.util.UUID;

public class BackService {
    private final StorageApi storage;
    public BackService(StorageApi storage) { this.storage = storage; }
    public void setLast(UUID uuid, Location loc) { storage.setLast(uuid, loc); }
    public Location getLast(UUID uuid) { return storage.getLast(uuid); }
}
