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
    private final PlayerVaultsConfig cfg;
    private final PlayerVaultsStorage storage;

    public PlayerVaultsService(OreoEssentials plugin) {
        this.plugin = plugin;
        this.cfg = new PlayerVaultsConfig(plugin);

        if (!cfg.enabled()) { this.storage = null; return; }

        final String pvMode   = cfg.storage().toLowerCase();
        final String mainMode = plugin.getConfig().getString("essentials.storage", "yaml").toLowerCase();
        final boolean wantMongo = pvMode.equals("mongodb") || (pvMode.equals("auto") && mainMode.equals("mongodb"));

        if (wantMongo) {
            MongoClient client = getHomesMongoClient(plugin);
            if (client != null) {
                String db   = plugin.getConfig().getString("storage.mongo.database", "oreo");
                String coll = cfg.collection();
                this.storage = new MongoPlayerVaultsStorage(client, db, coll, "global");
            } else {
                this.storage = new YamlPlayerVaultsStorage(plugin);
            }
        } else {
            this.storage = new YamlPlayerVaultsStorage(plugin);
        }
    }

    public boolean enabled() { return cfg.enabled() && storage != null; }

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

        if (!canAccess(p, id)) {
            deny(p, cfg.denyMessage().replace("%id%", String.valueOf(id)));
            return;
        }

        // 1) allowed SLOTS (config/perms)
        int allowedSlots = resolveSlots(p, id);
        allowedSlots = Math.max(1, Math.min(cfg.slotCap(), allowedSlots));

        // 2) visible rows (Bukkit inventories are multiples of 9)
        int rowsVisible = Math.max(1, (int) Math.ceil(allowedSlots / 9.0));
        int size = rowsVisible * 9;

        // 3) load snapshot and clamp to allowedSlots
        var snap = storage.load(p.getUniqueId(), id);
        org.bukkit.inventory.ItemStack[] contents = new org.bukkit.inventory.ItemStack[size];
        if (snap != null && snap.contents() != null) {
            System.arraycopy(snap.contents(), 0, contents, 0, Math.min(allowedSlots, snap.contents().length));
        }

        // 4) open masked view
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

    public void saveLimited(Player p, int id, int rowsVisible, int allowedSlots, org.bukkit.inventory.ItemStack[] inv) {
        org.bukkit.inventory.ItemStack[] toSave = new org.bukkit.inventory.ItemStack[allowedSlots];
        System.arraycopy(inv, 0, toSave, 0, Math.min(allowedSlots, inv.length));
        storage.save(p.getUniqueId(), id, rowsVisible, toSave);
    }

    public void stop() { try { if (storage != null) storage.flush(); } catch (Throwable ignored) {} }

    public boolean hasUsePermission(Player p, int id) {
        if (p.hasPermission("oreo.vault.bypass")
                || p.hasPermission("oreo.vault.use."+id)
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

        for (int n = cfg.slotCap(); n >= 1; n--) {
            if (p.hasPermission("oreo.vault.slots." + vaultId + "." + n)) return n;
        }
        for (int n = cfg.slotCap(); n >= 1; n--) {
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
