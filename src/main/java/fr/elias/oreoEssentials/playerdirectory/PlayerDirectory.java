// File: src/main/java/fr/elias/oreoEssentials/playerdirectory/PlayerDirectory.java
package fr.elias.oreoEssentials.playerdirectory;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.*;

public class PlayerDirectory {
    private final MongoCollection<Document> coll;

    public PlayerDirectory(MongoClient client, String db, String prefix) {
        this.coll = client.getDatabase(db).getCollection(prefix + "player_directory");
        ensureIndexes();
    }

    private void ensureIndexes() {
        try {
            coll.createIndex(new Document("uuid", 1));                       // fast UUID lookup
            coll.createIndex(new Document("nameLower", 1));                  // case-insensitive name lookup
        } catch (Throwable ignored) {}
    }

    /* --------------------------------------------------------
     * Basic mappings
     * -------------------------------------------------------- */

    /** Save or update (legacy compatibility). */
    public void saveMapping(String name, UUID uuid) {
        upsertPresence(uuid, name, null); // no server provided
    }

    /** Case-insensitive name → UUID. */
    public UUID lookupUuidByName(String name) {
        if (name == null || name.isBlank()) return null;
        Document doc = coll.find(eq("nameLower", name.toLowerCase(Locale.ROOT))).first();
        if (doc == null) return null;
        try { return UUID.fromString(doc.getString("uuid")); } catch (Exception e) { return null; }
    }

    /* --------------------------------------------------------
     * Presence (server) APIs
     * -------------------------------------------------------- */

    /**
     * Called on player join (or when you learn the player's server).
     * Sets both currentServer and lastServer to 'server' when provided.
     */
    public void upsertPresence(UUID uuid, String name, String server) {
        if (uuid == null) return;

        final String now = Instant.now().toString();
        Document doc = new Document("uuid", uuid.toString())
                .append("name", name == null ? "" : name)
                .append("nameLower", name == null ? "" : name.toLowerCase(Locale.ROOT))
                .append("updatedAt", now);

        if (server != null && !server.isBlank()) {
            doc.append("currentServer", server)
                    .append("lastServer", server);
        }

        coll.replaceOne(eq("uuid", uuid.toString()), doc, new ReplaceOptions().upsert(true));
    }

    /** Mark player as being on this server now (updates current & last). */
    public void setCurrentServer(UUID uuid, String server) {
        if (uuid == null || server == null || server.isBlank()) return;
        Bson filter = eq("uuid", uuid.toString());
        Bson update = combine(
                set("currentServer", server),
                set("lastServer", server),
                set("updatedAt", Instant.now().toString())
        );
        coll.updateOne(filter, update, new com.mongodb.client.model.UpdateOptions().upsert(true));
    }

    /** Mark player as offline (move current → last, clear current). */
    public void clearCurrentServer(UUID uuid) {
        if (uuid == null) return;
        Document doc = coll.find(eq("uuid", uuid.toString())).first();
        if (doc == null) return;

        String current = doc.getString("currentServer");
        Bson filter = eq("uuid", uuid.toString());
        Bson update = (current == null || current.isBlank())
                ? set("updatedAt", Instant.now().toString())
                : combine(
                set("lastServer", current),
                unset("currentServer"),
                set("updatedAt", Instant.now().toString())
        );
        coll.updateOne(filter, update);
    }

    /** Returns currentServer if set, otherwise lastServer; may be null. */
    public String getCurrentOrLastServer(UUID uuid) {
        if (uuid == null) return null;
        Document doc = coll.find(eq("uuid", uuid.toString())).first();
        if (doc == null) return null;
        String cur = doc.getString("currentServer");
        if (cur != null && !cur.isBlank()) return cur;
        String last = doc.getString("lastServer");
        return (last == null || last.isBlank()) ? null : last;
    }

    /** Convenience: update name casing if it changed. */
    public void updateName(UUID uuid, String name) {
        if (uuid == null || name == null) return;
        coll.updateOne(
                eq("uuid", uuid.toString()),
                combine(
                        set("name", name),
                        set("nameLower", name.toLowerCase(Locale.ROOT)),
                        set("updatedAt", Instant.now().toString())
                ),
                new com.mongodb.client.model.UpdateOptions().upsert(true)
        );
    }
}
