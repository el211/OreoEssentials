package fr.elias.oreoEssentials.database;


import fr.elias.oreoEssentials.offline.OfflinePlayerCache;
import java.util.UUID;

public interface PlayerEconomyDatabase {

    boolean connect(String url, String user, String password);

    void giveBalance(UUID playerUUID, String name, double amount);
    void takeBalance(UUID playerUUID, String name, double amount);
    void setBalance(UUID playerUUID, String name, double amount);

    double getBalance(UUID playerUUID);
    double getOrCreateBalance(UUID playerUUID, String name);

    void populateCache(OfflinePlayerCache cache);
    void clearCache();
    void close();

}
