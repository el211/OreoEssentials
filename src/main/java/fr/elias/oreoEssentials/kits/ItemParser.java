package fr.elias.oreoEssentials.kits;


import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;


import java.util.*;


public final class ItemParser {
    private ItemParser() {}


    public static boolean isItemsAdderPresent() {
        return Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
    }


    /**
     * Parses strings like:
     * - "type:STONE_SWORD;amount:1;name:&aSword;lore:&7Line1|&7Line2;enchants:SHARPNESS:2,UNBREAKING:1;flags:HIDE_ENCHANTS"
     * - "ia:namespace:item_name" (ItemsAdder)
     */
    public static ItemStack parseItem(String def, boolean useItemsAdder) {
        def = def.trim();
        if (def.toLowerCase(Locale.ROOT).startsWith("ia:")) {
            if (useItemsAdder && isItemsAdderPresent()) {
                try {
                    String key = def.substring(3);
                    Class<?> cs = Class.forName("dev.lone.itemsadder.api.CustomStack");
                    Object custom = cs.getMethod("byName", String.class).invoke(null, key);
                    if (custom != null) {
                        return (ItemStack) cs.getMethod("getItemStack").invoke(custom);
                    }
                } catch (Throwable ignored) { }
            }
// IA disabled or not found -> fall through with AIR
            return new ItemStack(Material.AIR);
        }


// Vanilla typed format
        Map<String, String> map = new LinkedHashMap<>();
        for (String part : def.split(";")) {
            int i = part.indexOf(':');
            if (i == -1) continue;
            map.put(part.substring(0, i).trim().toLowerCase(Locale.ROOT), part.substring(i + 1).trim());
        }


        Material mat = Material.matchMaterial(map.getOrDefault("type", "AIR"));
        if (mat == null) mat = Material.AIR;
        int amount = parseInt(map.get("amount"), 1);
        ItemStack item = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (map.containsKey("name")) meta.setDisplayName(color(map.get("name")));
            if (map.containsKey("lore")) {
                String[] lines = map.get("lore").split("\\|");
                List<String> lore = new ArrayList<>();
                for (String l : lines) lore.add(color(l));
                meta.setLore(lore);
            }
            if (map.containsKey("flags")) {
                for (String f : map.get("flags").split(",")) {
                    try { meta.addItemFlags(ItemFlag.valueOf(f.trim().toUpperCase(Locale.ROOT))); } catch (Exception ignored) {}
                }
            }
            item.setItemMeta(meta);
        }
        if (map.containsKey("enchants")) {
            for (String e : map.get("enchants").split(",")) {
                String[] kv = e.split(":");
                if (kv.length >= 1) {
                    Enchantment ench = Enchantment.getByName(kv[0].trim().toUpperCase(Locale.ROOT));
                    int level = (kv.length >= 2) ? parseInt(kv[1], 1) : 1;
                    if (ench != null) item.addUnsafeEnchantment(ench, level);
                }
            }
        }
        return item;
    }


    public static String color(String s) { return s.replace('&', '§'); }
    private static int parseInt(String s, int d) {
        try { return Integer.parseInt(s); } catch (Exception ignored) { return d; }
    }
}