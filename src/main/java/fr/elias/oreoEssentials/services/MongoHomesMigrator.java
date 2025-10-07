// File: src/main/java/fr/elias/oreoEssentials/services/MongoHomesMigrator.java
package fr.elias.oreoEssentials.services;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import java.util.Arrays;
import java.util.List;

public final class MongoHomesMigrator {

    private MongoHomesMigrator() {}

    /**
     * Normalizes/repairs the "server" field in both:
     *   - <prefix>homes
     *   - <prefix>home_directory
     *
     * It will:
     *  - change legacy names (e.g. "Purpur" or Bukkit server name) to desiredName
     *  - set missing/empty/NULL server fields to desiredName
     */
    public static void run(MongoClient client, String dbName, String prefix, String legacyName, String desiredName, java.util.logging.Logger log) {
        MongoDatabase db = client.getDatabase(dbName);
        MongoCollection<Document> homes = db.getCollection(prefix + "homes");
        MongoCollection<Document> dir   = db.getCollection(prefix + "home_directory");

        // Anything we want to consider "legacy"
        List<String> legacyCandidates = Arrays.asList(
                legacyName,         // e.g. "Purpur" (Bukkit server name)
                "Purpur",           // common default
                "purpur",           // case variants
                ""
        );

        long fixedMissingHomes = homes.updateMany(
                Filters.or(Filters.exists("server", false), Filters.eq("server", null), Filters.eq("server", "")),
                Updates.set("server", desiredName)
        ).getModifiedCount();

        long fixedMissingDir = dir.updateMany(
                Filters.or(Filters.exists("server", false), Filters.eq("server", null), Filters.eq("server", "")),
                Updates.set("server", desiredName)
        ).getModifiedCount();

        long fixedLegacyHomes = 0;
        long fixedLegacyDir   = 0;
        for (String legacy : legacyCandidates) {
            if (legacy == null || legacy.equals(desiredName)) continue;
            UpdateResult r1 = homes.updateMany(Filters.eq("server", legacy), Updates.set("server", desiredName));
            UpdateResult r2 = dir.updateMany(Filters.eq("server", legacy), Updates.set("server", desiredName));
            fixedLegacyHomes += r1.getModifiedCount();
            fixedLegacyDir   += r2.getModifiedCount();
        }

        log.info("[OreoEssentials] Mongo migration: homes fixed (missing=" + fixedMissingHomes + ", legacy=" + fixedLegacyHomes + "), "
                + "home_directory fixed (missing=" + fixedMissingDir + ", legacy=" + fixedLegacyDir + "). Target='" + desiredName + "'");
    }
}
