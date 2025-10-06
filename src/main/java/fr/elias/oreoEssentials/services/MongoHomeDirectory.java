package fr.elias.oreoEssentials.services;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;

import java.util.Locale;
import java.util.UUID;

public final class MongoHomeDirectory implements HomeDirectory {
    private final MongoCollection<Document> coll;

    public MongoHomeDirectory(MongoClient client, String dbName, String collectionName) {
        MongoDatabase db = client.getDatabase(dbName);
        this.coll = db.getCollection(collectionName);
        // unique on (uuid, name)
        this.coll.createIndex(Indexes.ascending("uuid", "name"),
                new IndexOptions().unique(true));
    }

    @Override
    public void setHomeServer(UUID uuid, String name, String server) {
        final String u = uuid.toString();
        final String n = name.toLowerCase(Locale.ROOT);
        Document key = new Document("uuid", u).append("name", n);
        Document doc = new Document(key).append("server", server);
        coll.replaceOne(Filters.and(Filters.eq("uuid", u), Filters.eq("name", n)),
                doc, new ReplaceOptions().upsert(true));
    }

    @Override
    public String getHomeServer(UUID uuid, String name) {
        final String u = uuid.toString();
        final String n = name.toLowerCase(Locale.ROOT);
        Document d = coll.find(Filters.and(Filters.eq("uuid", u), Filters.eq("name", n)))
                .projection(new Document("server", 1)).first();
        return d == null ? null : d.getString("server");
    }

    @Override
    public void deleteHome(UUID uuid, String name) {
        final String u = uuid.toString();
        final String n = name.toLowerCase(Locale.ROOT);
        coll.deleteOne(Filters.and(Filters.eq("uuid", u), Filters.eq("name", n)));
    }
}
