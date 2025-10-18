// File: src/main/java/fr/elias/oreoEssentials/customcraft/CustomCraftingListener.java
package fr.elias.oreoEssentials.customcraft;

import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Map;

public final class CustomCraftingListener implements Listener {
    private final CustomCraftingService service;

    public CustomCraftingListener(CustomCraftingService service) {
        this.service = service;
    }

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent e) {
        Recipe r = e.getRecipe();
        if (r == null || !(r instanceof Keyed keyed)) return;
        NamespacedKey key = keyed.getKey();

        service.getRecipeNameByKey(key).ifPresent(name -> {
            String perm = service.getPermissionFor(name).orElse(null);
            if (perm == null) return; // public recipe
            if (e.getView().getPlayer() instanceof Player p && !p.hasPermission(perm)) {
                e.getInventory().setResult(new ItemStack(Material.AIR)); // hide result
            }
        });
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        Recipe r = e.getRecipe();
        if (!(r instanceof Keyed keyed)) return;
        NamespacedKey key = keyed.getKey();

        service.getRecipeNameByKey(key).ifPresent(name -> {
            String perm = service.getPermissionFor(name).orElse(null);
            if (perm == null) return;
            if (e.getWhoClicked() instanceof Player p && !p.hasPermission(perm)) {
                e.setCancelled(true);
                p.sendMessage(tp("customcraft.messages.no-permission-craft", Map.of("permission", perm)));
            }
        });
    }

    /* ---------------- Lang helpers (key, default) ---------------- */

    private static String t(String path) {
        String s = Lang.get(path, path); // pass default
        return (s == null) ? path : s;
    }

    private static String tp(String path, Map<String, String> ph) {
        String s = t(path);
        if (ph == null || ph.isEmpty()) return s;
        for (var e : ph.entrySet()) {
            s = s.replace("%" + e.getKey() + "%", e.getValue());
        }
        return s;
    }
}
