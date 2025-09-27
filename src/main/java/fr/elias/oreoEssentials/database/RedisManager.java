package fr.elias.oreoEssentials.database;


import org.redisson.Redisson;
import org.redisson.api.LocalCachedMapOptions;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RMap;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RedisManager {
    private final RedissonClient redisson;
    private final boolean enabled;
    private final RMapCache<UUID, Double> balanceMap;

    public RedisManager(String host, int port, String password) {
        this.enabled = (host != null && !host.isEmpty());

        if (enabled) {
            Config config = new Config();
            if (password != null && !password.isEmpty()) {
                config.useSingleServer()
                        .setAddress("redis://" + host + ":" + port)
                        .setPassword(password);
            } else {
                config.useSingleServer()
                        .setAddress("redis://" + host + ":" + port);
            }

            redisson = Redisson.create(config);
            balanceMap = redisson.getMapCache("player_balances");
        } else {
            redisson = null;
            balanceMap = null;
        }
    }

    public boolean connect() {
        return enabled && redisson != null;
    }

    /**
     * Get a player's balance from Redis.
     * If the player does not exist in Redis, return `null` (fallback to database).
     */
    public Double getBalance(UUID playerUUID) {
        if (!enabled || balanceMap == null) return null;
        return balanceMap.getOrDefault(playerUUID, null);
    }

    /**
     * Store a player's balance in Redis and set expiration.
     */
    public void setBalance(UUID playerUUID, double balance) {
        if (!enabled || balanceMap == null) return;
        balanceMap.put(playerUUID, balance, 10, TimeUnit.MINUTES);
        // Set the expiration to 10 minutes
    }

    /**
     * Add money to a player's balance in Redis.
     */
    public void giveBalance(UUID playerUUID, double amount) {
        if (!enabled || balanceMap == null) return;
        balanceMap.compute(playerUUID, (key, currentBalance) ->
                (currentBalance == null ? 100.0 : currentBalance) + amount);
    }

    /**
     * Deduct money from a player's balance in Redis, ensuring it never goes below 0.
     */
    public void takeBalance(UUID playerUUID, double amount) {
        if (!enabled || balanceMap == null) return;
        balanceMap.compute(playerUUID, (key, currentBalance) ->
                Math.max(0, (currentBalance == null ? 100.0 : currentBalance) - amount));
    }

    /**
     * Remove a player's balance from Redis (forcing a fresh database fetch on next request).
     */
    public void deleteBalance(UUID playerUUID) {
        if (!enabled || balanceMap == null) return;
        balanceMap.remove(playerUUID);
    }

    /**
     * Clear all cached balances (forces all players to reload from the database).
     */
    public void clearCache() {
        if (!enabled || balanceMap == null) return;
        balanceMap.clear();
    }

    /**
     * Gracefully shutdown Redis connection.
     */
    public void shutdown() {
        if (redisson != null) {
            redisson.shutdown();
        }
    }
}
