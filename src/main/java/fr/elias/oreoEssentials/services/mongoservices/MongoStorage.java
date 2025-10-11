// File: src/main/java/fr/elias/oreoEssentials/services/MongoStorage.java
package fr.elias.oreoEssentials.services.mongoservices;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import fr.elias.oreoEssentials.services.ConfigService;
import fr.elias.oreoEssentials.services.StorageApi;
import fr.elias.oreoEssentials.util.LocUtil;
import org.bson.Document;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;
import static com.mongodb.client.model.Updates.unset;

public class MongoStorage implements StorageApi {
    private final Plugin plugin;
    private final MongoClient client;
    private final MongoDatabase db;
    private final MongoCollection<Document> colSpawn;
    private final MongoCollection<Document> colWarps;
    private final MongoCollection<Document> colPlayers;

    public MongoStorage(Plugin plugin, ConfigService cfg) {
        this.plugin = plugin;
        this.client = MongoClients.create(cfg.mongoUri());
        this.db = client.getDatabase(cfg.mongoDatabase());
        String p = cfg.mongoPrefix();
        this.colSpawn = db.getCollection(p + "spawn");
        this.colWarps = db.getCollection(p + "warps");
        this.colPlayers = db.getCollection(p + "playerdata");
        // indexes are optional here; data set is small
    }

    @Override public void setSpawn(Location loc) {
        Document d = new Document("_id", "spawn").append("loc", LocUtil.toDoc(loc));
        colSpawn.replaceOne(eq("_id", "spawn"), d, new com.mongodb.client.model.ReplaceOptions().upsert(true));
    }
    @Override public Location getSpawn() {
        Document d = colSpawn.find(eq("_id", "spawn")).first();
        if (d == null) return null;
        return LocUtil.fromDoc(d.get("loc", Document.class));
    }

    @Override public void setWarp(String name, Location loc) {
        String id = name.toLowerCase();
        Document d = new Document("_id", id).append("loc", LocUtil.toDoc(loc));
        colWarps.replaceOne(eq("_id", id), d, new com.mongodb.client.model.ReplaceOptions().upsert(true));
    }
    @Override public boolean delWarp(String name) {
        return colWarps.deleteOne(eq("_id", name.toLowerCase())).getDeletedCount() > 0;
    }
    @Override public Location getWarp(String name) {
        Document d = colWarps.find(eq("_id", name.toLowerCase())).first();
        if (d == null) return null;
        return LocUtil.fromDoc(d.get("loc", Document.class));
    }
    @Override public Set<String> listWarps() {
        FindIterable<Document> it = colWarps.find().projection(new Document("_id", 1));
        return java.util.stream.StreamSupport.stream(it.spliterator(), false)
                .map(doc -> doc.getString("_id"))
                .sorted()
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Override public boolean setHome(UUID uuid, String name, Location loc) {
        String id = uuid.toString();
        String key = "homes."+name.toLowerCase();
        colPlayers.updateOne(eq("_id", id),
                combine(set("_id", id), set(key, LocUtil.toDoc(loc))),
                new com.mongodb.client.model.UpdateOptions().upsert(true));
        return true;
    }
    @Override public boolean delHome(UUID uuid, String name) {
        String id = uuid.toString();
        String key = "homes."+name.toLowerCase();
        var res = colPlayers.updateOne(eq("_id", id), unset(key));
        return res.getModifiedCount() > 0;
    }
    @Override public Location getHome(UUID uuid, String name) {
        Document d = colPlayers.find(eq("_id", uuid.toString()))
                .projection(new Document("homes."+name.toLowerCase(), 1)).first();
        if (d == null) return null;
        Document homes = d.get("homes", Document.class);
        if (homes == null) return null;
        Document loc = homes.get(name.toLowerCase(), Document.class);
        if (loc == null) return null;
        return LocUtil.fromDoc(loc);
    }
    @Override public Set<String> homes(UUID uuid) {
        Document d = colPlayers.find(eq("_id", uuid.toString()))
                .projection(new Document("homes", 1)).first();
        if (d == null) return java.util.Set.of();
        Document homes = d.get("homes", Document.class);
        if (homes == null) return java.util.Set.of();
        return homes.keySet().stream().sorted().collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Override public void setLast(UUID uuid, Location loc) {
        String id = uuid.toString();
        colPlayers.updateOne(eq("_id", id),
                combine(set("_id", id), set("lastLocation", LocUtil.toDoc(loc))),
                new com.mongodb.client.model.UpdateOptions().upsert(true));
    }
    @Override public Location getLast(UUID uuid) {
        Document d = colPlayers.find(eq("_id", uuid.toString()))
                .projection(new Document("lastLocation", 1)).first();
        if (d == null) return null;
        Document loc = d.get("lastLocation", Document.class);
        if (loc == null) return null;
        return LocUtil.fromDoc(loc);
    }

    @Override public void flush() { /* no-op for Mongo */ }
    @Override public void close() {
        try { client.close(); } catch (Exception ignored) {}
    }
}
