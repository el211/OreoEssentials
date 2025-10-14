package fr.elias.oreoEssentials.cross;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.enderchest.EnderChestService;
import fr.elias.oreoEssentials.enderchest.EnderChestStorage;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.CrossInvPacket;
import fr.elias.oreoEssentials.services.InventoryService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class InvBridge {
    private static final Gson GSON = new Gson();

    public enum Kind { INV, EC }

    private final OreoEssentials plugin;
    private final PacketManager packets;
    private final String thisServer;
    private final Map<String, CompletableFuture<SnapshotResponse>> pending = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<ApplyAck>> pendingApply = new ConcurrentHashMap<>();
    private final long TIMEOUT_MS = 2500;

    public InvBridge(OreoEssentials plugin, PacketManager packets, String thisServer) {
        this.plugin = plugin;
        this.packets = packets;
        this.thisServer = thisServer;

        // ---- subscriptions ----
        packets.subscribe(CrossInvPacket.class, (channel, pkt) -> {
            try {
                final String payload = pkt.getJson();
                Base base = GSON.fromJson(payload, Base.class);
                if (base == null || base.type == null) return;

                switch (base.type) {
                    case "INV_REQ"   -> onRequest(payload);
                    case "INV_RESP"  -> onResponse(payload);
                    case "INV_APPLY" -> onApply(payload);
                    case "INV_ACK"   -> onAck(payload);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[INV-BRIDGE] Incoming packet error: " + t.getMessage());
            }
        });
    }

    /* ===================== messages ===================== */

    static class Base {
        @SerializedName("type") public String type;
    }

    static final class SnapshotRequest extends Base {
        public String correlationId;
        public String sourceServer;
        public UUID   target;
        public Kind   kind;
        public int    ecRows;
    }
    static final class SnapshotResponse extends Base {
        public String correlationId;
        public boolean ok;
        public String  error;
        public UUID    target;
        public Kind    kind;
        public byte[]  blob; // Bukkit-serialized ItemStack[] (raw bytes)
        public int     arrayLen;
    }

    static final class ApplyRequest extends Base {
        public String correlationId;
        public String sourceServer;
        public UUID   target;
        public Kind   kind;
        public byte[] blob;
        public int    arrayLen; // sanity
        public int    ecRows;   // for EC
    }
    static final class ApplyAck extends Base {
        public String correlationId;
        public boolean ok;
        public String  error;
    }

    /* ===================== public API ===================== */

    /** Ask "whoever has the player" for a live snapshot. Falls back to empty if nobody answers. */
    public InventoryService.Snapshot requestLiveInv(UUID target) {
        try {
            SnapshotResponse resp = requestSnapshot(target, Kind.INV, 0);
            if (resp != null && resp.ok && resp.blob != null) {
                ItemStack[] flat = BukkitSerialization.fromBytes(resp.blob, ItemStack[].class);
                return InvLayouts.fromFlat(flat);
            }
        } catch (Throwable ignored) {}
        return new InventoryService.Snapshot(); // empty
    }

    /** Ask for live EC. If nobody answers, return null to let callers fall back to storage. */
    public ItemStack[] requestLiveEc(UUID target, int rows) {
        try {
            SnapshotResponse resp = requestSnapshot(target, Kind.EC, rows);
            if (resp != null && resp.ok && resp.blob != null) {
                return BukkitSerialization.fromBytes(resp.blob, ItemStack[].class);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Apply inventory to live player (remote or local). Returns true on ack/OK. */
    public boolean applyLiveInv(UUID target, InventoryService.Snapshot snap) {
        ItemStack[] flat = InvLayouts.toFlat(snap);
        byte[] payload = BukkitSerialization.toBytes(flat);
        return applyRemote(target, Kind.INV, payload, flat.length, 0);
    }

    /** Apply ender chest to live player (remote or local). */
    public boolean applyLiveEc(UUID target, ItemStack[] ec, int rows) {
        ItemStack[] clamp = EnderChestStorage.clamp(ec, rows);
        byte[] payload = BukkitSerialization.toBytes(clamp);
        return applyRemote(target, Kind.EC, payload, clamp.length, rows);
    }

    /* ===================== request/response core ===================== */

    private SnapshotResponse requestSnapshot(UUID target, Kind kind, int ecRows) {
        String corr = UUID.randomUUID().toString();
        SnapshotRequest req = new SnapshotRequest();
        req.type = "INV_REQ";
        req.correlationId = corr;
        req.sourceServer  = this.thisServer;
        req.target        = target;
        req.kind          = kind;
        req.ecRows        = ecRows;

        CompletableFuture<SnapshotResponse> fut = new CompletableFuture<>();
        pending.put(corr, fut);
        publish(req);

        try {
            return fut.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            plugin.getLogger().fine("[INV-BRIDGE] Snapshot timeout for " + target + " kind=" + kind);
            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("[INV-BRIDGE] Snapshot error: " + e.getMessage());
            return null;
        } finally {
            pending.remove(corr);
        }
    }

    private boolean applyRemote(UUID target, Kind kind, byte[] payload, int arrayLen, int ecRows) {
        String corr = UUID.randomUUID().toString();
        ApplyRequest req = new ApplyRequest();
        req.type = "INV_APPLY";
        req.correlationId = corr;
        req.sourceServer  = this.thisServer;
        req.target        = target;
        req.kind          = kind;
        req.blob          = payload;
        req.arrayLen      = arrayLen;
        req.ecRows        = ecRows;

        CompletableFuture<ApplyAck> fut = new CompletableFuture<>();
        pendingApply.put(corr, fut);
        publish(req);

        try {
            ApplyAck ack = fut.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (ack == null) return false;
            if (!ack.ok) plugin.getLogger().warning("[INV-BRIDGE] Apply NACK: " + ack.error);
            return ack.ok;
        } catch (TimeoutException te) {
            plugin.getLogger().fine("[INV-BRIDGE] Apply timeout for " + target + " kind=" + kind);
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("[INV-BRIDGE] Apply error: " + e.getMessage());
            return false;
        } finally {
            pendingApply.remove(corr);
        }
    }

    private void publish(Object o) {
        String json = GSON.toJson(o);
        packets.sendPacket(PacketChannels.GLOBAL, new CrossInvPacket(json));
    }

    /* ===================== inbound handlers ===================== */

    private void onRequest(String json) {
        SnapshotRequest req = GSON.fromJson(json, SnapshotRequest.class);
        if (req == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            SnapshotResponse resp = new SnapshotResponse();
            resp.type = "INV_RESP";
            resp.correlationId = req.correlationId;
            resp.ok = false;
            resp.target = req.target;
            resp.kind = req.kind;

            Player p = Bukkit.getPlayer(req.target);
            if (p == null || !p.isOnline()) {
                resp.ok = false;
                resp.error = "Player not online on server=" + this.thisServer;
                publish(resp);
                return;
            }

            try {
                switch (req.kind) {
                    case INV -> {
                        InventoryService.Snapshot s = new InventoryService.Snapshot();
                        s.contents = Arrays.copyOf(p.getInventory().getContents(), 41);
                        s.armor    = Arrays.copyOf(p.getInventory().getArmorContents(), 4);
                        s.offhand  = p.getInventory().getItemInOffHand();
                        ItemStack[] flat = InvLayouts.toFlat(s);
                        resp.blob = BukkitSerialization.toBytes(flat);
                        resp.arrayLen = flat.length;
                        resp.ok = true;
                    }
                    case EC -> {
                        EnderChestService svc = Bukkit.getServicesManager().load(EnderChestService.class);
                        ItemStack[] ec;
                        if (p.getOpenInventory() != null &&
                                EnderChestService.TITLE.equals(p.getOpenInventory().getTitle())) {
                            ec = Arrays.copyOf(
                                    p.getOpenInventory().getTopInventory().getContents(),
                                    Math.max(1, req.ecRows) * 9
                            );
                        } else {
                            int rows = Math.max(1, req.ecRows);
                            ec = EnderChestStorage.clamp(svc.loadFor(p.getUniqueId(), rows), rows);
                        }
                        resp.blob = BukkitSerialization.toBytes(ec);
                        resp.arrayLen = ec.length;
                        resp.ok = true;
                    }
                }
            } catch (Throwable t) {
                resp.ok = false;
                resp.error = "Exception: " + t.getMessage();
            }
            publish(resp);
        });
    }

    private void onResponse(String json) {
        SnapshotResponse resp = GSON.fromJson(json, SnapshotResponse.class);
        if (resp == null) return;
        CompletableFuture<SnapshotResponse> fut = pending.get(resp.correlationId);
        if (fut != null) fut.complete(resp);
    }

    private void onApply(String json) {
        ApplyRequest req = GSON.fromJson(json, ApplyRequest.class);
        if (req == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            ApplyAck ack = new ApplyAck();
            ack.type = "INV_ACK";
            ack.correlationId = req.correlationId;
            ack.ok = false;

            Player p = Bukkit.getPlayer(req.target);
            if (p == null || !p.isOnline()) {
                ack.ok = false;
                ack.error = "Player not online on this server=" + this.thisServer;
                publish(ack);
                return;
            }

            try {
                switch (req.kind) {
                    case INV -> {
                        ItemStack[] flat = BukkitSerialization.fromBytes(req.blob, ItemStack[].class);
                        InventoryService.Snapshot snap = InvLayouts.fromFlat(flat);

                        var pi = p.getInventory();
                        ItemStack[] main = Arrays.copyOf(snap.contents, Math.max(pi.getContents().length, 41));
                        pi.setContents(main);
                        pi.setArmorContents(Arrays.copyOf(snap.armor, 4));
                        pi.setItemInOffHand(snap.offhand);
                        ack.ok = true;
                    }
                    case EC -> {
                        ItemStack[] ec = BukkitSerialization.fromBytes(req.blob, ItemStack[].class);
                        EnderChestService svc = Bukkit.getServicesManager().load(EnderChestService.class);

                        if (p.getOpenInventory() != null &&
                                EnderChestService.TITLE.equals(p.getOpenInventory().getTitle())) {
                            int allowed = svc.resolveSlots(p);
                            for (int i = 0; i < Math.min(allowed, ec.length); i++) {
                                p.getOpenInventory().getTopInventory().setItem(i, ec[i]);
                            }
                            svc.saveFromInventory(p, p.getOpenInventory().getTopInventory());
                        } else {
                            svc.saveFor(p.getUniqueId(), Math.max(1, req.ecRows), ec);
                        }
                        ack.ok = true;
                    }
                }
            } catch (Throwable t) {
                ack.ok = false;
                ack.error = "Exception: " + t.getMessage();
            }
            publish(ack);
        });
    }

    private void onAck(String json) {
        ApplyAck ack = GSON.fromJson(json, ApplyAck.class);
        if (ack == null) return;
        CompletableFuture<ApplyAck> fut = pendingApply.get(ack.correlationId);
        if (fut != null) fut.complete(ack);
    }

    /* ===================== helpers ===================== */

    /** Flatten/expand inv snapshot consistently across servers. */
    public static final class InvLayouts {
        // flat: [0..40] main(41), [41..44] armor(4), [45] offhand(1) => 46 total
        public static ItemStack[] toFlat(InventoryService.Snapshot s) {
            ItemStack[] flat = new ItemStack[46];
            ItemStack[] cont = s.contents == null ? new ItemStack[41] : Arrays.copyOf(s.contents, 41);
            ItemStack[] arm  = s.armor == null ? new ItemStack[4]    : Arrays.copyOf(s.armor, 4);
            for (int i = 0; i < 41; i++) flat[i] = cont[i];
            for (int i = 0; i < 4;  i++) flat[41 + i] = arm[i];
            flat[45] = s.offhand;
            return flat;
        }
        public static InventoryService.Snapshot fromFlat(ItemStack[] flat) {
            InventoryService.Snapshot s = new InventoryService.Snapshot();
            ItemStack[] src = flat == null ? new ItemStack[46] : flat;
            s.contents = Arrays.copyOfRange(src, 0, 41);
            s.armor    = Arrays.copyOfRange(src, 41, 45);
            s.offhand  = (src.length > 45 ? src[45] : null);
            return s;
        }
    }

    /** Simple (de)serialization of Bukkit objects via BukkitObject streams. */
    public static final class BukkitSerialization {
        public static byte[] toBytes(Object o) {
            try (var bos = new java.io.ByteArrayOutputStream();
                 var oos = new org.bukkit.util.io.BukkitObjectOutputStream(bos)) {
                oos.writeObject(o);
                oos.flush();
                return bos.toByteArray();
            } catch (Exception e) {
                return ("ERR:" + e.getMessage()).getBytes(StandardCharsets.UTF_8);
            }
        }
        @SuppressWarnings("unchecked")
        public static <T> T fromBytes(byte[] bytes, Class<T> type) {
            try (var bis = new java.io.ByteArrayInputStream(bytes);
                 var ois = new org.bukkit.util.io.BukkitObjectInputStream(bis)) {
                Object o = ois.readObject();
                return (T) o;
            } catch (Exception e) {
                return null;
            }
        }
    }
}
