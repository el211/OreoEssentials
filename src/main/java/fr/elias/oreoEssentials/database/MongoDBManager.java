// File: src/main/java/fr/elias/oreoEssentials/database/MongoDBManager.java
package fr.elias.oreoEssentials.database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import fr.elias.oreoEssentials.offline.OfflinePlayerCache;
import org.bson.Document;
import org.bukkit.Bukkit;

import java.util.Objects;
import java.util.UUID;

public class MongoDBManager implements PlayerEconomyDatabase {

    private final RedisManager redis;
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> collection;

    // TODO: feed these from config later
    private static final double STARTING_BALANCE = 100.0;
    private static final double MIN_BALANCE = 0.0;
    private static final double MAX_BALANCE = 1_000_000_000.0;
    private static final boolean ALLOW_NEGATIVE = false;

    public MongoDBManager(RedisManager redis) {
        this.redis = redis;
    }

    @Override
    public boolean connect(String uri, String database, String collection) {
        try {
            this.mongoClient = MongoClients.create(uri);
            this.database = mongoClient.getDatabase(database);
            this.collection = this.database.getCollection(collection);

            // Unique index on playerUUID for faster lookups & integrity
            this.collection.createIndex(Indexes.ascending("playerUUID"),
                    new IndexOptions().unique(true));

            System.out.println("[OreoEssentials] ✅ Connected to MongoDB: " + database);
            return true;
        } catch (Exception e) {
            System.err.println("[OreoEssentials] ❌ Failed to connect to MongoDB! Check credentials & server.");
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void giveBalance(UUID playerUUID, String name, double amount) {
        // Atomic increment in DB; then refresh cache
        String id = playerUUID.toString();
        try {
            collection.updateOne(Filters.eq("playerUUID", id),
                    Updates.combine(
                            Updates.set("playerUUID", id),
                            Updates.set("name", name),
                            Updates.inc("balance", amount)
                    ),
                    new UpdateOptions().upsert(true)
            );
            // Force cache refresh from DB source of truth
            redis.deleteBalance(playerUUID);
            redis.setBalance(playerUUID, getBalance(playerUUID));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void takeBalance(UUID playerUUID, String name, double amount) {
        // Respect MIN/ALLOW_NEGATIVE via clamp using setBalance()
        double current = getBalance(playerUUID);
        double target = current - amount;
        setBalance(playerUUID, name, target);
    }

    @Override
    public double getBalance(UUID playerUUID) {
        Double cached = redis.getBalance(playerUUID);
        if (cached != null) return cached;

        Document doc = collection.find(Filters.eq("playerUUID", playerUUID.toString())).first();
        if (doc != null) {
            double balance = readNumber(doc, "balance", STARTING_BALANCE);
            redis.setBalance(playerUUID, balance);
            return balance;
        }
        return STARTING_BALANCE;
    }

    @Override
    public double getOrCreateBalance(UUID playerUUID, String name) {
        Double cached = redis.getBalance(playerUUID);
        if (cached != null) return cached;

        Document doc = collection.find(Filters.eq("playerUUID", playerUUID.toString())).first();
        if (doc != null) {
            double balance = readNumber(doc, "balance", STARTING_BALANCE);
            redis.setBalance(playerUUID, balance);
            return balance;
        }

        setBalance(playerUUID, name, STARTING_BALANCE);
        return STARTING_BALANCE;
    }

    @Override
    public void setBalance(UUID playerUUID, String name, double amount) {
        String id = playerUUID.toString();

        double clamped = clamp(amount, MIN_BALANCE, MAX_BALANCE, ALLOW_NEGATIVE);

        Document doc = new Document("playerUUID", id)
                .append("name", name)
                .append("balance", clamped);

        try {
            collection.replaceOne(Filters.eq("playerUUID", id),
                    doc, new ReplaceOptions().upsert(true));

            // Cache new value
            redis.setBalance(playerUUID, clamped);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void populateCache(OfflinePlayerCache cache) {
        for (Document doc : collection.find()) {
            try {
                String s = doc.getString("playerUUID");
                if (s == null) continue;
                UUID id = UUID.fromString(s);
                String name = doc.getString("name");
                if (name == null) {
                    name = Bukkit.getOfflinePlayer(id).getName();
                }
                if (name != null) cache.add(name, id);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void clearCache() {
        redis.clearCache();
    }

    @Override
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            System.out.println("[OreoEssentials] MongoDB connection closed.");
        }
    }

    // ---- helpers ----

    private static double readNumber(Document doc, String key, double def) {
        Object v = doc.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return def;
    }

    private static double clamp(double value, double min, double max, boolean allowNegative) {
        double v = value;
        if (!allowNegative) v = Math.max(min, v);
        return Math.min(max, v);
    }
}
