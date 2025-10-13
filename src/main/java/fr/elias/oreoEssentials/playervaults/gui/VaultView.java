package fr.elias.oreoEssentials.playervaults.gui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.playervaults.PlayerVaultsConfig;
import fr.elias.oreoEssentials.playervaults.PlayerVaultsService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Objects;

public final class VaultView {

    private VaultView() {}

    public static void open(OreoEssentials plugin,
                            PlayerVaultsService svc,
                            PlayerVaultsConfig cfg,
                            Player player,
                            int id,
                            int rowsVisible,
                            int allowedSlots,
                            ItemStack[] initial) {

        int size = Math.max(9, rowsVisible * 9);
        String title = ChatColor.translateAlternateColorCodes('&',
                cfg.vaultTitle()
                        .replace("<id>", String.valueOf(id))
                        .replace("<rows>", String.valueOf(rowsVisible)));

        Inventory inv = Bukkit.createInventory(null, size, title);

        // Fill allowed slots with contents
        if (initial != null) {
            for (int i = 0; i < Math.min(allowedSlots, initial.length) && i < size; i++) {
                inv.setItem(i, initial[i]);
            }
        }

        // Fill blocked cells with barrier
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta m = barrier.getItemMeta();
        m.setDisplayName(ChatColor.RED + "Blocked");
        barrier.setItemMeta(m);

        for (int i = allowedSlots; i < size; i++) {
            inv.setItem(i, barrier);
        }

        Listener listener = new Listener() {
            @EventHandler
            public void onClick(InventoryClickEvent e) {
                if (!Objects.equals(e.getWhoClicked().getUniqueId(), player.getUniqueId())) return;
                if (!Objects.equals(e.getView().getTitle(), title)) return;
                if (e.getRawSlot() >= 0 && e.getRawSlot() < size && e.getRawSlot() >= allowedSlots) {
                    // Block interactions on barrier area
                    e.setCancelled(true);
                }
            }

            @EventHandler
            public void onClose(InventoryCloseEvent e) {
                if (!Objects.equals(e.getPlayer().getUniqueId(), player.getUniqueId())) return;
                if (!Objects.equals(e.getView().getTitle(), title)) return;

                // Save only the first allowedSlots
                ItemStack[] all = e.getInventory().getContents();
                svc.saveLimited(player, id, rowsVisible, allowedSlots, all);

                HandlerList.unregisterAll(this);
            }
        };

        Bukkit.getPluginManager().registerEvents(listener, plugin);
        player.openInventory(inv);
    }
}
