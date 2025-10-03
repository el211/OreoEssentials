package fr.elias.oreoEssentials.kits;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;


import java.util.*;


public class KitsMenu implements InventoryProvider {
    private final OreoEssentials plugin;
    private final KitsManager manager;


    public KitsMenu(OreoEssentials plugin, KitsManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }


    public SmartInventory build() {
        return SmartInventory.builder()
                .id("oreo-kits")
                .title(manager.getMenuTitle())
                .size(manager.getMenuRows(), 9)
                .provider(this)
                .manager(plugin.getCommands().getInvManager())
                .build();
    }


    @Override
    public void init(Player player, InventoryContents contents) {
// Optional fill
        if (manager.isMenuFill()) {
            Material filler = Material.matchMaterial(manager.getFillMaterial());
            if (filler == null) filler = Material.GRAY_STAINED_GLASS_PANE;
            ItemStack pane = new ItemStack(filler);
            contents.fill(ClickableItem.empty(pane));
        }


// Place kits
        int col = 0, row = 0;
        for (Kit k : manager.getKits().values()) {
            int targetSlot = -1;
            if (k.getSlot() != null) targetSlot = k.getSlot();
            ItemStack icon = k.getIcon().clone();
            ItemMeta meta = icon.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add("§7Cooldown: §e" + k.getCooldownSeconds() + "s");
            long left = manager.getSecondsLeft(player, k);
            if (left > 0) lore.add("§cAvailable in: §e" + left + "s"); else lore.add("§aAvailable now!");
            if (meta != null) {
                meta.setDisplayName(k.getDisplayName());
                List<String> prev = meta.getLore();
                if (prev != null) lore.addAll(prev);
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            ClickableItem click = ClickableItem.of(icon, e -> {
                manager.claim(player, k.getId());
// refresh open inventory after claim
                build().open(player);
            });


            if (targetSlot >= 0) {
                int r = targetSlot / 9; int c = targetSlot % 9;
                if (r < contents.rows() && c < contents.columns()) contents.set(r, c, click);
                continue;
            }


            contents.set(row, col, click);
            col++;
            if (col >= 9) { col = 0; row++; if (row >= contents.rows()) break; }
        }
    }


    @Override
    public void update(Player player, InventoryContents contents) {
// Could update cooldown lore live if desired
    }
}