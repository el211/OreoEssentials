// File: src/main/java/fr/elias/oreoEssentials/kits/KitsMenuSI.java
package fr.elias.oreoEssentials.kits;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import fr.minuskube.inv.content.SlotPos;
import fr.minuskube.inv.ClickableItem;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class KitsMenuSI implements InventoryProvider {

    private final OreoEssentials plugin;
    private final KitsManager manager;

    public KitsMenuSI(OreoEssentials plugin, KitsManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /** Open the SmartInvs menu for a player. */
    public static void open(OreoEssentials plugin, KitsManager manager, Player player) {
        int rows = Math.max(1, Math.min(6, manager.getMenuRows()));

        SmartInventory.builder()
                .id("oreo_kits_menu")
                .title(manager.getMenuTitle())
                .size(rows, 9)
                .provider(new KitsMenuSI(plugin, manager))
                .manager(plugin.getInvManager())
                .build()
                .open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        // Fill background if configured
        if (manager.isMenuFill()) {
            Material m = Material.matchMaterial(manager.getFillMaterial());
            if (m == null) m = Material.GRAY_STAINED_GLASS_PANE;
            ItemStack filler = new ItemStack(m);
            ItemMeta fim = filler.getItemMeta();
            if (fim != null) { fim.setDisplayName(" "); filler.setItemMeta(fim); }
            contents.fill(ClickableItem.empty(filler));
        }

        // Build ClickableItems for kits
        List<ClickableItem> buttons = new ArrayList<>();
        List<Kit> kitList = new ArrayList<>(manager.getKits().values()); // keep stable ordering

        for (Kit kit : kitList) {
            ItemStack icon = kit.getIcon() != null ? kit.getIcon().clone() : new ItemStack(Material.CHEST);
            ItemMeta meta = icon.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add("§7Click to claim");

            long left = manager.getSecondsLeft(player, kit);
            if (left > 0) lore.add("§cCooldown: " + left + "s left");
            else if (kit.getCooldownSeconds() > 0) lore.add("§aCooldown ready");

            if (meta != null) {
                meta.setDisplayName(kit.getDisplayName());
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }

            buttons.add(ClickableItem.of(icon, e -> {
                long cdLeft = manager.getSecondsLeft(player, kit);
                if (cdLeft > 0 && !player.hasPermission("oreo.kit.bypasscooldown")) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
                    player.sendMessage("§cYou must wait §e" + cdLeft + "s §cbefore claiming §6" + kit.getDisplayName() + "§c.");
                    contents.inventory().open(player); // refresh
                    return;
                }

                boolean handled = manager.claim(player, kit.getId());
                if (handled) {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                }
                contents.inventory().open(player); // refresh to update cooldown lore
            }));
        }

        int rows = contents.inventory().getRows();
        int cols = contents.inventory().getColumns();

        boolean anyFixed = manager.getKits().values().stream().anyMatch(k -> k.getSlot() != null);

        if (anyFixed) {
            // 1) Place all fixed-slot kits first
            for (int i = 0; i < kitList.size(); i++) {
                Kit k = kitList.get(i);
                if (k.getSlot() == null) continue;

                int slot = k.getSlot();
                if (slot < 0 || slot >= rows * cols) continue;

                int r = slot / cols;
                int c = slot % cols;

                contents.set(SlotPos.of(r, c), buttons.get(i));
            }

            // 2) Flow the rest into any empty positions
            int next = 0;
            outer:
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (contents.get(SlotPos.of(r, c)).isPresent()) continue;

                    // advance to next non-fixed item
                    while (next < kitList.size()) {
                        Kit k = kitList.get(next);
                        ClickableItem ci = buttons.get(next);
                        next++;
                        if (k.getSlot() == null) {
                            contents.set(SlotPos.of(r, c), ci);
                            break;
                        }
                    }
                    if (next >= kitList.size()) break outer;
                }
            }
        } else {
            // No fixed slots: use Pagination + SlotIterator
            Pagination pagination = contents.pagination();
            pagination.setItems(buttons.toArray(new ClickableItem[0]));
            pagination.setItemsPerPage(rows * cols);

            // Start top-left; override so we can keep filler appearance
            SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 0, 0);
            it.allowOverride(true);
            pagination.addToIterator(it);
        }
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        // We reopen on click; nothing needed here.
    }
}
