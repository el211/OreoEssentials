// File: src/main/java/fr/elias/oreoEssentials/enderchest/EnderChestService.java
package fr.elias.oreoEssentials.enderchest;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class EnderChestService {

    public static final String TITLE = Lang.color(Lang.get("enderchest.gui.title", "&5Ender Chest"));
    private static final int MAX_SIZE = 54; // 6 rows * 9

    private final OreoEssentials plugin;
    private final EnderChestConfig config;
    private final EnderChestStorage storage;
    private final NamespacedKey LOCK_KEY;

    public EnderChestService(OreoEssentials plugin, EnderChestConfig config, EnderChestStorage storage) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        this.LOCK_KEY = new NamespacedKey(plugin, "ec_locked");
    }

    public void open(Player p) {
        p.openInventory(createVirtualEc(p));
    }

    /** Build a 6-row GUI; only the first N slots are usable, rest are barriers. */
    public Inventory createVirtualEc(Player p) {
        int allowedSlots = resolveSlots(p);
        int rowsForStorage = Math.max(1, (int) Math.ceil(allowedSlots / 9.0));

        Inventory inv = Bukkit.createInventory(p, MAX_SIZE, TITLE);

        // Load previously saved items (we store only the "used capacity" part)
        ItemStack[] stored = storage.load(p.getUniqueId(), rowsForStorage);
        if (stored != null) {
            // put only into the first allowedSlots
            for (int i = 0; i < Math.min(stored.length, allowedSlots); i++) {
                inv.setItem(i, stored[i]);
            }
        }

        // Lock everything else with barriers
        for (int slot = allowedSlots; slot < MAX_SIZE; slot++) {
            inv.setItem(slot, lockedBarrierItem(allowedSlots));
        }
        return inv;
    }

    /** Save only the first N slots; ignore locked area entirely. */
    public void saveFromInventory(Player p, Inventory inv) {
        try {
            int allowed = resolveSlots(p);
            int rowsForStorage = Math.max(1, (int) Math.ceil(allowed / 9.0));

            ItemStack[] src = inv.getContents();
            ItemStack[] toSave = new ItemStack[allowed];
            for (int i = 0; i < allowed && i < src.length; i++) {
                ItemStack it = src[i];
                toSave[i] = (isLockItem(it) ? null : it);
            }
            storage.save(p.getUniqueId(), rowsForStorage, toSave);
        } catch (Throwable t) {
            plugin.getLogger().warning("[EC] Save failed for " + p.getUniqueId() + ": " + t.getMessage());
            // Lang message: enderchest.storage.save-failed
            fr.elias.oreoEssentials.util.Lang.send(
                    p,
                    "enderchest.storage.save-failed",
                    null,
                    p
            );
        }
    }
    // in EnderChestService
    public ItemStack[] loadFor(java.util.UUID uuid, int rows) {
        return storage.load(uuid, rows);
    }
    public void saveFor(java.util.UUID uuid, int rows, ItemStack[] contents) {
        storage.save(uuid, rows, contents);
    }

    /* ---------------- permissions / slots ---------------- */

    public int resolveSlots(Player p) {
        int slots = config.getDefaultSlots();
        Map<String, Integer> ranks = config.getRankSlots();
        for (var e : ranks.entrySet()) {
            String node = ("oreo.tier." + e.getKey()).toLowerCase(Locale.ROOT);
            if (p.hasPermission(node)) {
                slots = Math.max(slots, e.getValue());
            }
        }
        return Math.max(1, Math.min(slots, MAX_SIZE));
    }

    /* ---------------- listener helpers ---------------- */

    /** Whether a raw slot (0..53) is locked for this player. */
    public boolean isLockedSlot(Player p, int rawSlot) {
        if (rawSlot < 0 || rawSlot >= MAX_SIZE) return false;
        return rawSlot >= resolveSlots(p);
    }

    public boolean isLockItem(ItemStack it) {
        if (it == null || it.getType() != Material.BARRIER) return false;
        try {
            ItemMeta meta = it.getItemMeta();
            if (meta == null) return false;
            Integer mark = meta.getPersistentDataContainer().get(LOCK_KEY, PersistentDataType.INTEGER);
            return mark != null && mark == 1;
        } catch (Throwable ignored) { return false; }
    }

    private ItemStack lockedBarrierItem(int allowedSlots) {
        ItemStack b = new ItemStack(Material.BARRIER);
        ItemMeta m = b.getItemMeta();
        if (m != null) {
            // 👇 THIS reads `enderchest.gui.locked-slot-name`
            String name = Lang.get("enderchest.gui.locked-slot-name", "&cLocked slot");
            m.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

            // 👇 THIS reads `enderchest.gui.locked-slot-lore` (list)
            List<String> rawLore = Lang.getList("enderchest.gui.locked-slot-lore");
            List<String> lore = new ArrayList<>();
            for (String line : rawLore) {
                line = line.replace("%slots%", String.valueOf(allowedSlots)); // 👈 %slots% replaced here
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            m.setLore(lore);

            m.getPersistentDataContainer().set(LOCK_KEY, PersistentDataType.INTEGER, 1);
            b.setItemMeta(m);
        }
        return b;
    }


}
