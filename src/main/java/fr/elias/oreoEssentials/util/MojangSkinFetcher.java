// File: src/main/java/fr/elias/oreoEssentials/util/MojangSkinFetcher.java
package fr.elias.oreoEssentials.util;

import org.bukkit.Bukkit;
import org.bukkit.profile.PlayerProfile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UUID lookups via Mojang API (minimal regex),
 * then let Bukkit fetch textures via PlayerProfile#update().
 */
public final class MojangSkinFetcher {

    private MojangSkinFetcher() {}

    /* ---------- tiny HTTP helper ---------- */
    private static String readUrl(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(7000);
        conn.setRequestProperty("User-Agent", "OreoEssentials/skin-fetcher");
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String ln;
            while ((ln = br.readLine()) != null) sb.append(ln);
            return sb.toString();
        }
    }

    // {"id":"<32 hex>","name":"..."}
    private static final Pattern UUID_ID =
            Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");

    /** Fetch UUID for a given username, or null if not found. */
    public static UUID fetchUuid(String name) {
        try {
            String body = readUrl("https://api.mojang.com/users/profiles/minecraft/" + name);
            Matcher m = UUID_ID.matcher(body);
            if (!m.find()) return null;
            String raw = m.group(1);
            return fromFlatUuid(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Create a Bukkit PlayerProfile and try to populate it with textures.
     * Returns the (possibly updated) profile. Never throws; may return a profile
     * without textures if Mojang is unavailable.
     */
    public static PlayerProfile fetchProfileWithTextures(UUID uuid, String nameForProfile) {
        if (uuid == null) return null;
        try {
            PlayerProfile prof = Bukkit.getServer().createPlayerProfile(uuid, nameForProfile);
            // Try to complete the profile (fills textures, etc.). Do a short, bounded wait.
            try {
                prof = prof.update().get(5, TimeUnit.SECONDS);
            } catch (Throwable ignored) {
                // If update fails or times out, just return the partial profile.
            }
            return prof;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /* ---------- helpers ---------- */
    private static String flatUuid(UUID u) {
        return u.toString().replace("-", "");
    }

    private static UUID fromFlatUuid(String s) {
        // turn 32-hex into dashed UUID
        return UUID.fromString(
                s.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                        "$1-$2-$3-$4-$5")
        );
    }
}
