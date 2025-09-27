package fr.elias.oreoEssentials.database;


import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.offline.OfflinePlayerCache;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;

public class PostgreSQLManager implements PlayerEconomyDatabase{
    private Connection connection;
    private final OreoEssentials plugin;
    private final RedisManager redis;

    public PostgreSQLManager(OreoEssentials plugin, RedisManager redis) {
        this.plugin = plugin;
        this.redis = redis;
    }

    public boolean connect(String url, String user, String password) {
        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection(url, user, password);
            plugin.getLogger().info("✅ Connected to PostgreSQL database!");

            // ✅ Ensure the economy table exists
            String createTableQuery = "CREATE TABLE IF NOT EXISTS economy (" +
                    "player_uuid UUID PRIMARY KEY, " +
                    "name TEXT, " +
                    "balance DOUBLE PRECISION NOT NULL DEFAULT 100.0)";
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(createTableQuery);
            }
            return true;

        } catch (SQLException e) {
            plugin.getLogger().severe("❌ Failed to connect to PostgreSQL!");
            e.printStackTrace();
            return false;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("✅ PostgreSQL connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ Error closing PostgreSQL connection!");
            e.printStackTrace();
        }
    }

    @Override
    public double getBalance(UUID playerUUID) {
        Double cachedBalance = redis.getBalance(playerUUID);
        if (cachedBalance != null) {
            return cachedBalance;
        }

        String query = "SELECT balance FROM economy WHERE player_uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, playerUUID);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                double balance = rs.getDouble("balance");
                redis.setBalance(playerUUID, balance);
                return balance;
            }



        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "❌ Error fetching balance from PostgreSQL", e);
        }
        return 100.0;
    }

    @Override
    public double getOrCreateBalance(UUID playerUUID, String name) {
        Double cachedBalance = redis.getBalance(playerUUID);
        if (cachedBalance != null) {
            return cachedBalance;
        }

        String query = "SELECT balance FROM economy WHERE player_uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, playerUUID);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                double balance = rs.getDouble("balance");
                redis.setBalance(playerUUID, balance);
                return balance;
            }

            setBalance(playerUUID, name, 100.0);
            return 100.0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "❌ Error fetching balance from PostgreSQL", e);
        }

        return 100.0;
    }

    @Override
    public void giveBalance(UUID playerUUID, String name, double amount) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            double newBalance = getBalance(playerUUID) + amount;
            setBalance(playerUUID, name, newBalance);
            redis.giveBalance(playerUUID, amount);
        });
    }

    @Override
    public void takeBalance(UUID playerUUID, String name, double amount) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            double newBalance = Math.max(0, getBalance(playerUUID) - amount);
            setBalance(playerUUID, name, newBalance);
            redis.takeBalance(playerUUID, amount);
        });
    }

    @Override
    public void setBalance(UUID playerUUID, String name, double amount) {
        String query = "INSERT INTO economy (player_uuid, name, balance) VALUES (?, ?, ?) " +
                "ON CONFLICT (player_uuid) DO UPDATE SET balance = EXCLUDED.balance";

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setObject(1, playerUUID);
                stmt.setString(2, name);
                stmt.setDouble(3, amount);
                stmt.executeUpdate();
                redis.setBalance(playerUUID, amount);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "❌ Error updating balance in PostgreSQL", e);
            }
        });
    }

    @Override
    public void populateCache(OfflinePlayerCache cache) {
        String query = "SELECT player_uuid, name FROM economy";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                UUID playerUUID = rs.getObject("player_uuid", UUID.class);
                String name = rs.getString("name");

                if (name == null) {
                    name = Bukkit.getOfflinePlayer(playerUUID).getName(); // No guarantees that this will work, geyser weird
                }

                if (name != null) {
                    cache.add(name, playerUUID);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "❌ Error populating cache in PostgreSQL", e);
        }
    }

    public void deleteBalance(UUID playerUUID) {
        String query = "DELETE FROM economy WHERE player_uuid = ?";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerUUID.toString());
                stmt.executeUpdate();
                redis.deleteBalance(playerUUID);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "❌ Error deleting balance from PostgreSQL", e);
            }
        });
    }

    public void clearCache() {
        redis.clearCache();
    }
}

