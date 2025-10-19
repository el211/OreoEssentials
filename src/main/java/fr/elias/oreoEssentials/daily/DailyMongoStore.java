package fr.elias.oreoEssentials.daily;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import fr.elias.oreoEssentials.OreoEssentials;
import org.bson.Document;

import java.time.LocalDate;
import java.util.UUID;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.*;

public final class DailyMongoStore {

    public static final class Record {
        public UUID uuid;
        public String name;
        public int streak;                // current consecutive days
        public long lastClaimEpochDay;    // LocalDate.toEpochDay()
        public int totalClaims;

        public LocalDate lastClaimDate() {
            return lastClaimEpochDay <= 0 ? null : LocalDate.ofEpochDay(lastClaimEpochDay);
        }
    }

    private final OreoEssentials plugin;
    private final DailyConfig cfg;
    private MongoClient client;
    private MongoCollection<Document> col;

    public DailyMongoStore(OreoEssentials plugin, DailyConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    public void connect() {
        if (!cfg.mongo.enabled) {
            plugin.getLogger().warning("[Daily] Mongo disabled in config, but DailyMongoStore created.");
            return;
        }
        client = MongoClients.create(cfg.mongo.uri);
        MongoDatabase db = client.getDatabase(cfg.mongo.database);
        col = db.getCollection(cfg.mongo.collection);
        plugin.getLogger().info("[Daily] Connected to MongoDB " + cfg.mongo.database + "." + cfg.mongo.collection);
    }

    public void close() {
        try { if (client != null) client.close(); } catch (Throwable ignored) {}
    }

    public Record get(UUID uuid) {
        Document d = col.find(eq("_id", uuid.toString())).first();
        if (d == null) return null;
        Record r = new Record();
        r.uuid = uuid;
        r.name = d.getString("name");
        r.streak = d.getInteger("streak", 0);
        r.lastClaimEpochDay = d.getLong("lastClaimEpochDay") == null ? 0L : d.getLong("lastClaimEpochDay");
        r.totalClaims = d.getInteger("totalClaims", 0);
        return r;
    }

    public Record ensure(UUID uuid, String name) {
        Record r = get(uuid);
        if (r != null) return r;
        Document d = new Document("_id", uuid.toString())
                .append("name", name)
                .append("streak", 0)
                .append("lastClaimEpochDay", 0L)
                .append("totalClaims", 0);
        col.insertOne(d);
        r = new Record();
        r.uuid = uuid; r.name = name;
        r.streak = 0; r.lastClaimEpochDay = 0; r.totalClaims = 0;
        return r;
    }

    public void updateOnClaim(UUID uuid, String name, int newStreak, LocalDate date) {
        col.updateOne(eq("_id", uuid.toString()),
                combine(
                        set("name", name),
                        set("streak", newStreak),
                        set("lastClaimEpochDay", date.toEpochDay()),
                        inc("totalClaims", 1)
                ),
                new com.mongodb.client.model.UpdateOptions().upsert(true)
        );
    }

    public void resetStreak(UUID uuid) {
        col.updateOne(eq("_id", uuid.toString()),
                combine(set("streak", 0), set("lastClaimEpochDay", 0L)));
    }
}
