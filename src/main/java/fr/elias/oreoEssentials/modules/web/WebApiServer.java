package fr.elias.oreoEssentials.modules.web;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.elias.oreoEssentials.OreoEssentials;
import org.bson.Document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WebApiServer {

    private final OreoEssentials plugin;
    private final WebProfileService webService;
    private final Gson gson = new Gson();

    private HttpServer server;
    private ExecutorService executor;

    public WebApiServer(OreoEssentials plugin, WebProfileService webService) {
        this.plugin = plugin;
        this.webService = webService;
    }

    public synchronized void start() {
        if (server != null) {
            plugin.getLogger().info("[WebProfile] Web API already started.");
            return;
        }

        try {
            String host = plugin.getConfig().getString("webprofile.api.host", "127.0.0.1");
            int port = plugin.getConfig().getInt("webprofile.api.port", 3001);

            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            executor = Executors.newFixedThreadPool(
                    plugin.getConfig().getInt("webprofile.api.threads", 4)
            );
            server.setExecutor(executor);

            server.createContext("/api/health", this::handleHealth);
            server.createContext("/api/login", this::handleLogin);
            server.createContext("/api/verify", this::handleVerify);
            server.createContext("/api/profile", this::handleProfile);

            server.start();
            plugin.getLogger().info("[WebProfile] Web API started on http://" + host + ":" + port);

        } catch (Exception e) {
            plugin.getLogger().severe("[WebProfile] Failed to start Web API: " + e.getMessage());
            e.printStackTrace();
            stop();
        }
    }

    public synchronized void stop() {
        if (server != null) {
            try {
                server.stop(0);
            } catch (Throwable ignored) {}
            server = null;
        }
        if (executor != null) {
            try {
                executor.shutdownNow();
            } catch (Throwable ignored) {}
            executor = null;
        }
    }



    private void handleHealth(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, json(false, "Method not allowed"));
            return;
        }
        JsonObject res = new JsonObject();
        res.addProperty("status", "ok");
        res.addProperty("time", Instant.now().toString());
        sendJson(ex, 200, gson.toJson(res));
    }

    private void handleLogin(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;

        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, json(false, "Method not allowed"));
            return;
        }

        JsonObject body = readJsonBody(ex);
        String username = body.has("username") ? safeString(body.get("username").getAsString()) : null;
        String password = body.has("password") ? safeString(body.get("password").getAsString()) : null;

        if (isNullOrEmpty(username) || isNullOrEmpty(password)) {
            sendJson(ex, 400, json(false, "Missing username/password"));
            return;
        }

        try {
            WebProfileService.WebProfileData data = webService.verifyLogin(username, password).join();
            if (data == null) {
                sendJson(ex, 401, json(false, "Invalid username or password"));
                return;
            }

            String token = webService.createSession(data.getUuid()).join();
            if (token == null) {
                sendJson(ex, 500, json(false, "Failed to create session"));
                return;
            }

            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("token", token);
            sendJson(ex, 200, gson.toJson(res));

        } catch (Throwable t) {
            plugin.getLogger().warning("[WebProfile] /api/login error: " + t.getMessage());
            sendJson(ex, 500, json(false, "Internal server error"));
        }
    }

    private void handleVerify(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;

        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, json(false, "Method not allowed"));
            return;
        }

        UUID uuid = requireAuth(ex);
        if (uuid == null) return;

        JsonObject res = new JsonObject();
        res.addProperty("success", true);
        sendJson(ex, 200, gson.toJson(res));
    }

    private void handleProfile(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;

        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, json(false, "Method not allowed"));
            return;
        }

        UUID uuid = requireAuth(ex);
        if (uuid == null) return;

        try {
            Document doc = webService.getProfileData(uuid).join();
            if (doc == null) {
                sendJson(ex, 404, json(false, "Profile not found"));
                return;
            }

            // Mongo Document -> JSON
            sendJson(ex, 200, doc.toJson());

        } catch (Throwable t) {
            plugin.getLogger().warning("[WebProfile] /api/profile error: " + t.getMessage());
            sendJson(ex, 500, json(false, "Internal server error"));
        }
    }


    private boolean handleCors(HttpExchange ex) throws IOException {
        Headers h = ex.getResponseHeaders();

        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");

        h.set("Content-Type", "application/json; charset=utf-8");

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return true;
        }
        return false;
    }

    private UUID requireAuth(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            sendJson(ex, 401, json(false, "Missing token"));
            return null;
        }

        String token = auth.substring("Bearer ".length()).trim();
        if (isNullOrEmpty(token)) {
            sendJson(ex, 401, json(false, "Missing token"));
            return null;
        }

        try {
            UUID uuid = webService.validateSession(token).join();
            if (uuid == null) {
                sendJson(ex, 401, json(false, "Invalid/expired token"));
                return null;
            }
            return uuid;
        } catch (Throwable t) {
            sendJson(ex, 401, json(false, "Invalid/expired token"));
            return null;
        }
    }

    private JsonObject readJsonBody(HttpExchange ex) throws IOException {
        String raw = readAll(ex.getRequestBody());
        if (raw == null) raw = "";
        raw = raw.trim();

        if (raw.isEmpty()) return new JsonObject();

        try {
            JsonObject obj = gson.fromJson(raw, JsonObject.class);
            return (obj != null ? obj : new JsonObject());
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }

    private void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] out = json.getBytes(StandardCharsets.UTF_8);

        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");

        ex.sendResponseHeaders(code, out.length);
        ex.getResponseBody().write(out);
        ex.close();
    }

    private String json(boolean success, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("success", success);
        if (message != null) o.addProperty("message", message);
        return gson.toJson(o);
    }

    private String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r = in.read(buf)) != -1) {
            baos.write(buf, 0, r);
        }
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static boolean isNullOrEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String safeString(String s) {
        return s == null ? null : s.trim();
    }
}
