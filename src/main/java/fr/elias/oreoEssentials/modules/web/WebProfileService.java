    package fr.elias.oreoEssentials.modules.web;

    import com.mongodb.client.MongoClient;
    import com.mongodb.client.MongoCollection;
    import com.mongodb.client.model.IndexOptions;
    import com.mongodb.client.model.ReplaceOptions;
    import fr.elias.oreoEssentials.OreoEssentials;
    import org.bson.Document;
    import org.bukkit.Bukkit;
    import org.bukkit.OfflinePlayer;
    import org.bukkit.entity.Player;

    import javax.crypto.SecretKeyFactory;
    import javax.crypto.spec.PBEKeySpec;
    import java.security.SecureRandom;
    import java.time.Instant;
    import java.util.Base64;
    import java.util.Date;
    import java.util.UUID;
    import java.util.concurrent.CompletableFuture;
    import java.util.concurrent.TimeUnit;

    import static com.mongodb.client.model.Filters.eq;


    public final class WebProfileService {

        private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        private static final SecureRandom RANDOM = new SecureRandom();

        private final OreoEssentials plugin;

        private final MongoCollection<Document> profiles;
        private final MongoCollection<Document> sessions;

        public WebProfileService(OreoEssentials plugin, MongoClient mongoClient) {
            this.plugin = plugin;

            String dbName = plugin.getConfig().getString("storage.mongo.database", "oreo");
            String prefix = plugin.getConfig().getString("storage.mongo.collectionPrefix", "oreo_");

            this.profiles = mongoClient.getDatabase(dbName).getCollection(prefix + "web_profiles");
            this.sessions = mongoClient.getDatabase(dbName).getCollection(prefix + "web_sessions");

            ensureIndexes();
        }

        private void ensureIndexes() {
            try {
                profiles.createIndex(new Document("uuid", 1), new IndexOptions().unique(true));
            } catch (Exception ignored) {}

            try {
                profiles.createIndex(new Document("usernameLower", 1), new IndexOptions().unique(true));
            } catch (Exception ignored) {}

            try {
                sessions.createIndex(new Document("token", 1), new IndexOptions().unique(true));
            } catch (Exception ignored) {}

            try {
                sessions.createIndex(
                        new Document("expiresAt", 1),
                        new IndexOptions().expireAfter(0L, TimeUnit.SECONDS)
                );
            } catch (Exception ignored) {}

            plugin.getLogger().info("[WebProfile] Index check complete");
        }

        public boolean hasProfile(UUID playerId) {
            try {
                return profiles.find(eq("uuid", playerId.toString())).first() != null;
            } catch (Exception e) {
                plugin.getLogger().warning("[WebProfile] Failed to check profile existence: " + e.getMessage());
                return false;
            }
        }


        public CompletableFuture<String> createSession(UUID playerId) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    if (playerId == null) return null;

                    boolean single = plugin.getConfig().getBoolean("webprofile.sessions.single", false);
                    if (single) {
                        sessions.deleteMany(eq("uuid", playerId.toString()));
                    }

                    String token = generateRandomString(48);
                    Instant expires = Instant.now().plusSeconds(7L * 24 * 3600);

                    Document doc = new Document()
                            .append("token", token)
                            .append("uuid", playerId.toString())
                            .append("createdAt", new Date())
                            .append("expiresAt", Date.from(expires));

                    sessions.insertOne(doc);
                    return token;

                } catch (Exception e) {
                    plugin.getLogger().warning("[WebProfile] Failed to create session: " + e.getMessage());
                    return null;
                }
            });
        }

        public CompletableFuture<UUID> validateSession(String token) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    if (token == null || token.isBlank()) return null;

                    Document doc = sessions.find(eq("token", token)).first();
                    if (doc == null) return null;

                    Date exp = doc.getDate("expiresAt");
                    if (exp != null && exp.toInstant().isBefore(Instant.now())) {
                        sessions.deleteOne(eq("token", token));
                        return null;
                    }

                    String uuidStr = doc.getString("uuid");
                    if (uuidStr == null) return null;

                    return UUID.fromString(uuidStr);
                } catch (Exception ignored) {
                    return null;
                }
            });
        }


        public CompletableFuture<WebCredentials> createProfile(Player player) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    if (player == null) return null;

                    String username = generateUniqueUsername(player);
                    String password = generateSecurePassword();
                    String passwordHash = hashPasswordPBKDF2(password);

                    Document doc = new Document()
                            .append("uuid", player.getUniqueId().toString())
                            .append("username", username)
                            .append("usernameLower", username.toLowerCase())
                            .append("passwordHash", passwordHash)
                            .append("playerName", player.getName())
                            .append("createdAt", new Date())
                            .append("lastLogin", null)
                            .append("loginCount", 0)
                            .append("enabled", true);

                    profiles.replaceOne(
                            eq("uuid", player.getUniqueId().toString()),
                            doc,
                            new ReplaceOptions().upsert(true)
                    );

                    plugin.getLogger().info("[WebProfile] Created profile for " + player.getName() + " (username: " + username + ")");
                    return new WebCredentials(username, password);

                } catch (Exception e) {
                    plugin.getLogger().severe("[WebProfile] Failed to create profile: " + e.getMessage());
                    e.printStackTrace();
                    return null;
                }
            });
        }


        public CompletableFuture<String> resetPassword(UUID playerId) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    if (playerId == null) return null;

                    String newPassword = generateSecurePassword();
                    String passwordHash = hashPasswordPBKDF2(newPassword);

                    profiles.updateOne(
                            eq("uuid", playerId.toString()),
                            new Document("$set", new Document()
                                    .append("passwordHash", passwordHash)
                                    .append("updatedAt", new Date()))
                    );

                    plugin.getLogger().info("[WebProfile] Reset password for UUID: " + playerId);
                    return newPassword;

                } catch (Exception e) {
                    plugin.getLogger().severe("[WebProfile] Failed to reset password: " + e.getMessage());
                    return null;
                }
            });
        }


        public CompletableFuture<WebProfileData> verifyLogin(String username, String password) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    if (username == null || username.isBlank() || password == null || password.isBlank()) return null;

                    String uLower = username.toLowerCase();
                    Document doc = profiles.find(eq("usernameLower", uLower)).first();

                    if (doc == null) {
                        doc = profiles.find(eq("username", username)).first();
                    }
                    if (doc == null) return null;

                    if (!doc.getBoolean("enabled", true)) return null;

                    String storedHash = doc.getString("passwordHash");
                    if (!verifyPasswordPBKDF2(password, storedHash)) return null;

                    profiles.updateOne(
                            eq("uuid", doc.getString("uuid")),
                            new Document("$set", new Document("lastLogin", new Date()))
                                    .append("$inc", new Document("loginCount", 1))
                    );

                    return new WebProfileData(
                            UUID.fromString(doc.getString("uuid")),
                            doc.getString("username"),
                            doc.getString("playerName")
                    );

                } catch (Exception e) {
                    plugin.getLogger().severe("[WebProfile] Login verification failed: " + e.getMessage());
                    return null;
                }
            });
        }


        public CompletableFuture<Document> getProfileData(UUID playerId) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    if (playerId == null) return null;

                    Document webProfile = profiles.find(eq("uuid", playerId.toString())).first();
                    if (webProfile == null) return null;

                    Document profileData = new Document();

                    profileData.append("username", webProfile.getString("username"));
                    profileData.append("playerName", webProfile.getString("playerName"));
                    profileData.append("uuid", playerId.toString());
                    profileData.append("createdAt", webProfile.get("createdAt"));
                    profileData.append("lastLogin", webProfile.get("lastLogin"));
                    profileData.append("loginCount", webProfile.getInteger("loginCount", 0));

                    if (plugin.getCurrencyService() != null) {
                        profileData.append("currencies", getCurrencyBalances(playerId));
                    }

                    if (plugin.getVaultEconomy() != null) {
                        try {
                            OfflinePlayer off = Bukkit.getOfflinePlayer(playerId);
                            double balance = plugin.getVaultEconomy().getBalance(off);
                            profileData.append("vaultBalance", balance);
                        } catch (Exception ignored) {}
                    }

                    if (plugin.getHomeService() != null) {
                        try {
                            int homesCount = plugin.getHomeService().allHomeNames(playerId).size();
                            profileData.append("homesCount", homesCount);
                        } catch (Exception ignored) {}
                    }

                    if (plugin.getPlaytimeTracker() != null) {
                        try {
                            long seconds = plugin.getPlaytimeTracker().getSeconds(playerId);
                            profileData.append("playtimeSeconds", seconds);
                            profileData.append("playtimeFormatted", formatPlaytime(seconds));
                        } catch (Exception ignored) {}
                    }

                    Player p = Bukkit.getPlayer(playerId);
                    boolean online = p != null && p.isOnline();
                    profileData.append("online", online);
                    if (online) {
                        profileData.append("currentServer", plugin.getServerNameSafe());
                    }

                    return profileData;

                } catch (Exception e) {
                    plugin.getLogger().severe("[WebProfile] Failed to get profile data: " + e.getMessage());
                    return null;
                }
            });
        }



        private String generateUniqueUsername(Player player) {
            String base = player.getName().toLowerCase();
            for (int i = 0; i < 15; i++) {
                String candidate = base + "_" + generateRandomString(4);
                Document exists = profiles.find(eq("usernameLower", candidate.toLowerCase())).first();
                if (exists == null) return candidate;
            }
            return base + "_" + generateRandomString(8);
        }

        private String generateSecurePassword() {
            return generateRandomString(16);
        }

        private String generateRandomString(int length) {
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
            }
            return sb.toString();
        }


        private String hashPasswordPBKDF2(String password) {
            try {
                int iterations = 120_000;
                byte[] salt = new byte[16];
                RANDOM.nextBytes(salt);

                byte[] hash = pbkdf2(password.toCharArray(), salt, iterations, 32);
                return "pbkdf2$" + iterations + "$" +
                        Base64.getEncoder().encodeToString(salt) + "$" +
                        Base64.getEncoder().encodeToString(hash);

            } catch (Exception e) {
                throw new RuntimeException("Failed to hash password", e);
            }
        }

        private boolean verifyPasswordPBKDF2(String password, String stored) {
            try {
                if (stored == null) return false;

                if (!stored.startsWith("pbkdf2$")) {
                    return legacySha256(password).equals(stored);
                }

                String[] parts = stored.split("\\$");
                if (parts.length != 4) return false;

                int iterations = Integer.parseInt(parts[1]);
                byte[] salt = Base64.getDecoder().decode(parts[2]);
                byte[] expected = Base64.getDecoder().decode(parts[3]);

                byte[] actual = pbkdf2(password.toCharArray(), salt, iterations, expected.length);

                if (actual.length != expected.length) return false;
                int diff = 0;
                for (int i = 0; i < actual.length; i++) diff |= (actual[i] ^ expected[i]);
                return diff == 0;

            } catch (Exception ignored) {
                return false;
            }
        }

        private byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLenBytes) throws Exception {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLenBytes * 8);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return skf.generateSecret(spec).getEncoded();
        }

        private String legacySha256(String password) {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                byte[] hash = md.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(hash);
            } catch (Exception e) {
                return "";
            }
        }

        private Document getCurrencyBalances(UUID playerId) {
            Document balances = new Document();
            try {
                var currencies = plugin.getCurrencyService().getAllCurrencies();
                for (var currency : currencies) {
                    double balance = plugin.getCurrencyService().getBalance(playerId, currency.getId()).join();
                    balances.append(currency.getId(), new Document()
                            .append("balance", balance)
                            .append("formatted", currency.format(balance))
                            .append("symbol", currency.getSymbol()));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[WebProfile] Failed to get currency balances: " + e.getMessage());
            }
            return balances;
        }

        private String formatPlaytime(long seconds) {
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            long minutes = (seconds % 3600) / 60;

            if (days > 0) return days + "d " + hours + "h " + minutes + "m";
            if (hours > 0) return hours + "h " + minutes + "m";
            return minutes + "m";
        }



        public static final class WebCredentials {
            private final String username;
            private final String password;

            public WebCredentials(String username, String password) {
                this.username = username;
                this.password = password;
            }

            public String getUsername() { return username; }
            public String getPassword() { return password; }
        }

        public static final class WebProfileData {
            private final UUID uuid;
            private final String username;
            private final String playerName;

            public WebProfileData(UUID uuid, String username, String playerName) {
                this.uuid = uuid;
                this.username = username;
                this.playerName = playerName;
            }

            public UUID getUuid() { return uuid; }
            public String getUsername() { return username; }
            public String getPlayerName() { return playerName; }
        }
    }
