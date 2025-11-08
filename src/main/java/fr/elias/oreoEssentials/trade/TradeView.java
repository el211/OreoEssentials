package fr.elias.oreoEssentials.trade;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class TradeView implements InventoryProvider {

    private final TradeSession session;
    private final TradeConfig cfg;
    private final boolean forA; // true = rendering for player A

    private TradeView(TradeConfig cfg, TradeSession session, boolean forA) {
        this.cfg = cfg;
        this.session = session;
        this.forA = forA;
    }

    public static SmartInventory build(OreoEssentials plugin, TradeConfig cfg, TradeSession session, boolean forA) {
        return SmartInventory.builder()
                .id("oreo:trade:" + (forA ? "A" : "B"))
                .provider(new TradeView(cfg, session, forA))
                .size(cfg.rows, 9)
                .title(ChatColor.translateAlternateColorCodes('&',
                        cfg.guiTitle.replace("<you>", cfg.youLabel).replace("<them>", cfg.themLabel)))
                .manager(plugin.getInvManager())
                .build();
    }

    @Override public void init(Player p, InventoryContents c) { drawStatic(p, c); }
    @Override public void update(Player p, InventoryContents c) { drawButtons(c); }

    private void drawStatic(Player p, InventoryContents c) {
        // divider
        ItemStack divider = named(cfg.dividerMaterial, cfg.dividerName);
        for (int r = 0; r < cfg.rows; r++) {
            c.set(r, 4, ClickableItem.empty(divider));
        }

        // labels
        c.set(0, 1, ClickableItem.empty(named(Material.PAPER, ChatColor.GOLD + cfg.youLabel)));
        c.set(0, 7, ClickableItem.empty(named(Material.PAPER, ChatColor.GOLD + cfg.themLabel)));

        drawButtons(c);
    }

    private void drawButtons(InventoryContents c) {
        int r = cfg.rows - 1;

        boolean meReady   = session.isConfirmed(forA);
        boolean themReady = session.isConfirmed(!forA);

        // your confirm (left bottom)
        c.set(SlotPos.of(r, 1), ClickableItem.of(
                named(meReady ? cfg.confirmedMaterial : cfg.confirmMaterial,
                        meReady ? cfg.confirmedText : cfg.confirmText),
                e -> session.clickConfirm(forA)));

        // cancel (middle bottom)
        c.set(SlotPos.of(r, 4), ClickableItem.of(named(cfg.cancelMaterial, cfg.cancelText),
                e -> session.clickCancel()));

        // their state (right bottom) – lamp only
        c.set(SlotPos.of(r, 7), ClickableItem.empty(
                named(themReady ? cfg.confirmedMaterial : Material.REDSTONE_LAMP,
                        themReady ? (ChatColor.GREEN + "Partner Ready") : (ChatColor.RED + "Waiting…"))));
    }

    private static ItemStack named(Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            it.setItemMeta(m);
        }
        return it;
    }

    /* ===== Helpers used by session ===== */

    /**
     * Snapshot the offer area from the viewer’s currently open top inventory.
     * @param viewer the player whose view we’re reading
     * @param rows   total rows of the GUI
     * @param forA   true to read the left half (columns 0..3), false for right half (5..8)
     */
    public static ItemStack[] snapshotOfferFrom(Player viewer, int rows, boolean forA) {
        if (viewer == null || viewer.getOpenInventory() == null) return new ItemStack[0];
        Inventory top = viewer.getOpenInventory().getTopInventory();
        if (top == null) return new ItemStack[0];

        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (int r = 1; r < rows - 1; r++) {
            int from = forA ? 0 : 5;
            int to   = forA ? 3 : 8;
            for (int x = from; x <= to; x++) {
                ItemStack it = top.getItem(r * 9 + x);
                if (it != null && !it.getType().isAir()) out.add(it.clone());
            }
        }
        return out.toArray(ItemStack[]::new);
    }
}
