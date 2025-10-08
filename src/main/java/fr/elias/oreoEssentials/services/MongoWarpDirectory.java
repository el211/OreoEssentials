// src/main/java/fr/elias/oreoEssentials/services/MongoWarpDirectory.java
package fr.elias.oreoEssentials.services;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import org.bson.Document;

import java.util.Locale;

public class MongoWarpDirectory implements WarpDirectory {
    private final MongoCollection<Document> col;

    // collectionName example: prefix + "warp_directory"
    public MongoWarpDirectory(MongoClient client, String dbName, String collectionName) {
        this.col = client.getDatabase(dbName).getCollection(collectionName);
        col.createIndex(Indexes.ascending("name"), new IndexOptions().unique(true));
    }

    private static String key(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }

    @Override
    public void setWarpServer(String warpName, String server) {
        String n = key(warpName);
        var keyDoc = new Document("name", n);
        var doc = new Document("name", n).append("server", server);
        col.replaceOne(keyDoc, doc, new ReplaceOptions().upsert(true));
    }

    @Override
    public String getWarpServer(String warpName) {
        String n = key(warpName);
        var d = col.find(Filters.eq("name", n)).projection(new Document("server", 1)).first();
        return d == null ? null : d.getString("server");
    }

    @Override
    public void deleteWarp(String warpName) {
        col.deleteOne(Filters.eq("name", key(warpName)));
    }
}
