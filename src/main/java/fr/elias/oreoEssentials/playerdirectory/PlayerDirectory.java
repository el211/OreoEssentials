package fr.elias.oreoEssentials.playerdirectory;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoClient;
import org.bson.Document;

import java.util.UUID;

public class PlayerDirectory {
    private final MongoCollection<Document> coll;

    public PlayerDirectory(MongoClient client, String db, String prefix) {
        this.coll = client.getDatabase(db).getCollection(prefix + "player_directory");
    }

    // Save or update mapping (call on player join)
    public void saveMapping(String name, UUID uuid) {
        Document doc = new Document("name", name)
                .append("uuid", uuid.toString());
        coll.replaceOne(new Document("name", name), doc, new com.mongodb.client.model.ReplaceOptions().upsert(true));
    }

    // Lookup UUID by name
    public UUID lookupUuidByName(String name) {
        Document doc = coll.find(new Document("name", name)).first();
        if (doc == null) return null;
        try {
            return UUID.fromString(doc.getString("uuid"));
        } catch (Exception e) {
            return null;
        }
    }
}