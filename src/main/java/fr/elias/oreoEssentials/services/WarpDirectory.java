// src/main/java/fr/elias/oreoEssentials/services/WarpDirectory.java
package fr.elias.oreoEssentials.services;

public interface WarpDirectory {
    void setWarpServer(String warpName, String server);
    String getWarpServer(String warpName);
    void deleteWarp(String warpName);
}
