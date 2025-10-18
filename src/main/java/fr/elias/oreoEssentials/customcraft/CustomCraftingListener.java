package fr.elias.oreoEssentials.customcraft;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.Keyed;

public final class CustomCraftingListener implements Listener {
    private final CustomCraftingService service;
    public CustomCraftingListener(CustomCraftingService service) { this.service = service; }

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent e) {
        Recipe r = e.getRecipe();
        if (!(r instanceof Keyed keyed)) return;
        NamespacedKey key = keyed.getKey();

        service.getRecipeNameByKey(key).ifPresent(name -> {
            String perm = service.getPermissionFor(name).orElse(null);
            if (perm == null) return; // no restriction
            // If anyone in the view is a player and lacks the perm, hide result
            if (e.getView().getPlayer() instanceof Player p && !p.hasPermission(perm)) {
                e.getInventory().setResult(new ItemStack(Material.AIR));
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
                p.sendMessage("§cYou lack permission to craft this: §e" + perm);
            }
        });
    }
}
