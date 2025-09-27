// File: src/main/java/fr/elias/oreoEssentials/services/WarpService.java
package fr.elias.oreoEssentials.services;

import org.bukkit.Location;

import java.util.Set;

public class WarpService {
    private final StorageApi storage;
    public WarpService(StorageApi storage) { this.storage = storage; }
    public boolean setWarp(String name, Location loc) { storage.setWarp(name, loc); return true; }
    public boolean delWarp(String name) { return storage.delWarp(name); }
    public Location getWarp(String name) { return storage.getWarp(name); }
    public Set<String> listWarps() { return storage.listWarps(); }
}
