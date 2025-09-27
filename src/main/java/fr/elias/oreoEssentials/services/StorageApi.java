// File: src/main/java/fr/elias/oreoEssentials/services/StorageApi.java
package fr.elias.oreoEssentials.services;

import org.bukkit.Location;

import java.util.Set;
import java.util.UUID;

public interface StorageApi {
    // spawn
    void setSpawn(Location loc);
    Location getSpawn();

    // warps
    void setWarp(String name, Location loc);
    boolean delWarp(String name);
    Location getWarp(String name);
    Set<String> listWarps();

    // homes
    boolean setHome(UUID uuid, String name, Location loc);
    boolean delHome(UUID uuid, String name);
    Location getHome(UUID uuid, String name);
    Set<String> homes(UUID uuid);

    // back
    void setLast(UUID uuid, Location loc);
    Location getLast(UUID uuid);

    void flush();
    void close();
}
