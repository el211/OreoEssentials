// File: src/main/java/fr/elias/oreoEssentials/kits/ItemParser.java
package fr.elias.oreoEssentials.kits;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemParser {
    private ItemParser() {}

    public static boolean isItemsAdderPresent() {
        return Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
    }

    public static String color(String s) {
        return s == null ? "" : s.replace('&','§').replace("\\n","\n");
    }

    /**
     * Accepts:
     *  - "ia:namespace:item_id" (ItemsAdder, if present & allowed)
     *  - "MATERIAL" (vanilla)
     *  - "type:MATERIAL;amount:1;enchants:SHARPNESS:2,UNBREAKING:1"
     */
    public static ItemStack parseItem(String def, boolean allowItemsAdder) {
        if (def == null || def.isBlank()) return null;

        // ItemsAdder short form
        if (allowItemsAdder && def.startsWith("ia:")) {
            try {
                // Requires ItemsAdder at runtime
                // dev.lone.itemsadder.api.CustomStack
                Class<?> cs = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Object custom = cs.getMethod("getInstance", String.class).invoke(null, def.substring(3));
                if (custom != null) {
                    Object is = cs.getMethod("getItemStack").invoke(custom);
                    return (ItemStack) is;
                }
            } catch (Throwable ignored) {
                // fallback below
            }
        }

        if (!def.contains(":") || def.toLowerCase(Locale.ROOT).startsWith("type:")) {
            return parseVanilla(def);
        }

        // allow shortest: "DIAMOND_SWORD"
        Material mat = Material.matchMaterial(def.trim());
        if (mat != null) return new ItemStack(mat, 1);

        return null;
    }

    private static final Pattern TYPE = Pattern.compile("type:([A-Z0-9_]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT = Pattern.compile("amount:(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENCH = Pattern.compile("enchants:([A-Za-z0-9_:,]+)", Pattern.CASE_INSENSITIVE);

    private static ItemStack parseVanilla(String def) {
        String d = def.trim();

        // If it's just a material name
        Material shortMat = Material.matchMaterial(d);
        if (shortMat != null) return new ItemStack(shortMat, 1);

        // Else parse "type:...,amount:...,enchants:..."
        Matcher mt = TYPE.matcher(d);
        if (!mt.find()) return null;
        Material mat = Material.matchMaterial(mt.group(1).toUpperCase(Locale.ROOT));
        if (mat == null) return null;

        int amount = 1;
        Matcher ma = AMOUNT.matcher(d);
        if (ma.find()) {
            try { amount = Math.max(1, Integer.parseInt(ma.group(1))); } catch (Exception ignored) {}
        }

        ItemStack is = new ItemStack(mat, amount);

        Matcher me = ENCH.matcher(d);
        if (me.find()) {
            String blob = me.group(1);
            String[] parts = blob.split(",");
            for (String p : parts) {
                String[] kv = p.split(":");
                if (kv.length >= 1) {
                    String name = kv[0].trim().toUpperCase(Locale.ROOT);
                    int lvl = 1;
                    if (kv.length >= 2) {
                        try { lvl = Integer.parseInt(kv[1].trim()); } catch (Exception ignored) {}
                    }
                    try {
                        Enchantment ench = Enchantment.getByName(name);
                        if (ench != null) is.addUnsafeEnchantment(ench, lvl);
                    } catch (Throwable ignored) {}
                }
            }
        }

        return is;
    }
}
