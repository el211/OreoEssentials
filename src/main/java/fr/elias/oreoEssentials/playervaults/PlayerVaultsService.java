package fr.elias.oreoEssentials.playervaults;

import com.mongodb.client.MongoClient;
import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.playervaults.gui.VaultMenu;
import fr.elias.oreoEssentials.playervaults.gui.VaultView;
import fr.elias.oreoEssentials.playervaults.storage.MongoPlayerVaultsStorage;
import fr.elias.oreoEssentials.playervaults.storage.YamlPlayerVaultsStorage;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class PlayerVaultsService {

    private final OreoEssentials plugin;
    private PlayerVaultsConfig cfg;
    private PlayerVaultsStorage storage;


    public PlayerVaultsService(OreoEssentials plugin) {
        this.plugin = plugin;
        reload(); // initial boot
    }
    /** Re-read config and (re)create storage if needed. Safe to call at runtime. */
    public synchronized void reload() {
        PlayerVaultsConfig newCfg = new PlayerVaultsConfig(plugin);

        // If disabled now, drop storage and update cfg
        if (!newCfg.enabled()) {
            try { if (storage != null) storage.flush(); } catch (Throwable ignored) {}
            storage = null;
            cfg = newCfg;
            plugin.getLogger().info("[Vaults] Disabled by config.");
            return;
        }

        // Decide desired backend (mongodb vs yaml) based on pv + essentials
        final String pvMode   = newCfg.storage().toLowerCase();
        final String mainMode = plugin.getConfig().getString("essentials.storage", "yaml").toLowerCase();
        final boolean wantMongo = pvMode.equals("mongodb") || (pvMode.equals("auto") && "mongodb".equals(mainMode));

        // Build new storage instance if needed
        PlayerVaultsStorage newStorage;
        if (wantMongo) {
            com.mongodb.client.MongoClient client = getHomesMongoClient(plugin);
            if (client != null) {
                String db   = plugin.getConfig().getString("storage.mongo.database", "oreo");
                String coll = newCfg.collection();
                newStorage  = new MongoPlayerVaultsStorage(client, db, coll, "global");
            } else {
                // Fallback to YAML if no shared Mongo client
                newStorage = new YamlPlayerVaultsStorage(plugin);
            }
        } else {
            newStorage = new YamlPlayerVaultsStorage(plugin);
        }

        // Swap cfg + storage (flush old if backend actually changed)
        PlayerVaultsStorage old = this.storage;
        this.storage = newStorage;
        this.cfg = newCfg;

        if (old != null && old.getClass() != newStorage.getClass()) {
            try { old.flush(); } catch (Throwable ignored) {}
        }

        plugin.getLogger().info("[Vaults] Reloaded (storage=" +
                (newStorage instanceof MongoPlayerVaultsStorage ? "mongodb" : "yaml") + ").");
    }


    public boolean enabled() { return cfg != null && cfg.enabled() && storage != null; }

    /* ---------------- GUI entry points ---------------- */

    public void openMenu(Player p) {
        if (!enabled()) return;
        if (!p.hasPermission("oreo.vault.menu") && !p.hasPermission("oreo.vault.bypass")) {
            deny(p, cfg.denyMessage().replace("%id%", "menu"));
            return;
        }
        var inv = VaultMenu.build(plugin, this, cfg, getAvailableIds());
        inv.open(p);
        p.playSound(p.getLocation(), cfg.openSound(), 1f, 1f);
    }

    public void openVault(Player p, int id) {
        if (!enabled()) return;

        // access check (perm or unlocked-by-rank)
        if (!canAccess(p, id)) {
            deny(p, cfg.denyMessage().replace("%id%", String.valueOf(id)));
            return;
        }

        // 1) allowed SLOTS (config/perms)
        int allowedSlots = resolveSlots(p, id);
        allowedSlots = Math.max(1, Math.min(cfg.slotsCap(), allowedSlots));

        // 2) visible rows (Bukkit inventories are multiples of 9)
        int rowsVisible = Math.max(1, (int) Math.ceil(allowedSlots / 9.0));
        int size = rowsVisible * 9;

        // 3) load snapshot and clamp to allowedSlots
        var snap = storage.load(p.getUniqueId(), id);
        org.bukkit.inventory.ItemStack[] contents = new org.bukkit.inventory.ItemStack[size];
        if (snap != null && snap.contents() != null) {
            System.arraycopy(snap.contents(), 0, contents, 0, Math.min(allowedSlots, snap.contents().length));
        }

        // 4) open masked view (view will mask blocked cells, and save only the allowed region)
        VaultView.open(plugin, this, cfg, p, id, rowsVisible, allowedSlots, contents);
        p.playSound(p.getLocation(), cfg.openSound(), 1f, 1f);
    }

    /** First N vault IDs unlocked by rank unless explicit perm nodes grant access. */
    public boolean canAccess(Player p, int id) {
        if (hasUsePermission(p, id)) return true;
        String rank = getPrimaryGroup(p);
        int unlocked = cfg.unlockedCountFor(rank);
        return id >= 1 && id <= unlocked;
    }

    /** Called by the view when saving; strips blocked cells before persisting. */
    public void saveLimited(Player p, int id, int rowsVisible, int allowedSlots, org.bukkit.inventory.ItemStack[] inv) {
        org.bukkit.inventory.ItemStack[] toSave = new org.bukkit.inventory.ItemStack[allowedSlots];
        System.arraycopy(inv, 0, toSave, 0, Math.min(allowedSlots, inv.length));
        storage.save(p.getUniqueId(), id, rowsVisible, toSave);
    }

    public void stop() {
        try { if (storage != null) storage.flush(); } catch (Throwable ignored) {}
    }

    /* ---------------- Permissions / slot logic ---------------- */

    public boolean hasUsePermission(Player p, int id) {
        if (p.hasPermission("oreo.vault.bypass")
                || p.hasPermission("oreo.vault.use." + id)
                || p.hasPermission("oreo.vault.use.*")) return true;

        // rank-based auto unlocks
        String group = getPrimaryGroup(p);
        int unlocked = cfg.unlockedCountFor(group);
        return id >= 1 && id <= unlocked;
    }

    /** Resolve allowed **slots** (config rank rules first, then permission fallbacks, then default). */
    public int resolveSlots(Player p, int vaultId) {
        String rank = getPrimaryGroup(p);
        int fromCfg = cfg.slotsFromConfig(vaultId, rank);
        if (fromCfg > 0) return fromCfg;

        for (int n = cfg.slotsCap(); n >= 1; n--) {
            if (p.hasPermission("oreo.vault.slots." + vaultId + "." + n)) return n;
        }
        for (int n = cfg.slotsCap(); n >= 1; n--) {
            if (p.hasPermission("oreo.vault.slots.global." + n)) return n;
        }
        return cfg.defaultSlots();
    }

    /* ---------------- internals ---------------- */

    private static MongoClient getHomesMongoClient(OreoEssentials plugin) {
        try {
            Field f = OreoEssentials.class.getDeclaredField("homesMongoClient");
            f.setAccessible(true);
            Object obj = f.get(plugin);
            if (obj instanceof MongoClient mc) return mc;
        } catch (Throwable ignored) {}
        return null;
    }

    private void deny(Player p, String msg) {
        p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        p.playSound(p.getLocation(), cfg.denySound(), 1f, 0.7f);
    }

    private List<Integer> getAvailableIds() {
        List<Integer> ids = new ArrayList<>();
        for (int i = 1; i <= cfg.maxVaults(); i++) ids.add(i);
        return ids;
    }

    private String getPrimaryGroup(Player p) {
        try {
            net.luckperms.api.LuckPerms api = net.luckperms.api.LuckPermsProvider.get();
            var user = api.getUserManager().getUser(p.getUniqueId());
            if (user != null && user.getPrimaryGroup() != null) return user.getPrimaryGroup();
        } catch (Throwable ignored) {}
        return "default";
    }
}
