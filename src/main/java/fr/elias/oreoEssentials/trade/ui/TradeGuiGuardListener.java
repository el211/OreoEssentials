// File: src/main/java/fr/elias/oreoEssentials/trade/ui/TradeGuiGuardListener.java
package fr.elias.oreoEssentials.trade.ui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;

import java.util.Optional;

public final class TradeGuiGuardListener implements Listener {
    private final OreoEssentials plugin;

    public TradeGuiGuardListener(OreoEssentials plugin) { this.plugin = plugin; }

    private Optional<SmartInventory> currentInv(Player p) {
        try { return plugin.getInvManager().getInventory(p); }
        catch (Throwable t) { return Optional.empty(); }
    }
    private Optional<InventoryContents> contents(Player p) {
        try { return plugin.getInvManager().getContents(p); }
        catch (Throwable t) { return Optional.empty(); }
    }

    /** Only guard when the open SmartInvs is our TradeMenu */
    private boolean isTradeMenuOpen(Player p) {
        Optional<SmartInventory> inv = currentInv(p);
        return inv.isPresent() && (inv.get().getProvider() instanceof TradeMenu);
    }

    private boolean isTopNonEditable(Player p, int rawSlot) {
        if (rawSlot < 0) return false;
        Inventory top = p.getOpenInventory().getTopInventory();
        if (top == null) return false;
        int size = top.getSize();
        if (rawSlot >= size) return false; // not the top inv
        int row = rawSlot / 9, col = rawSlot % 9;

        Optional<InventoryContents> oc = contents(p);
        if (oc.isEmpty()) return false; // not a SmartInvs GUI
        InventoryContents c = oc.get();

        boolean editable;
        try { editable = c.isEditable(SlotPos.of(row, col)); }
        catch (Throwable ignored) { editable = false; }
        return !editable;
    }

    // Allow clicks in editable top slots; block everything else touching top
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isTradeMenuOpen(p)) return;

        Inventory top = p.getOpenInventory().getTopInventory();
        boolean clickIsTop = e.getClickedInventory() == top;

        boolean topSlotEditable = clickIsTop && !isTopNonEditable(p, e.getSlot());

        // If clicking a non-editable top slot → block
        if (clickIsTop && !topSlotEditable) {
            e.setCancelled(true);
            e.setResult(Event.Result.DENY);
            return;
        }

        // For global actions (shift, swap, collect, clone), block unless this is an editable top-slot action
        switch (e.getAction()) {
            case COLLECT_TO_CURSOR, MOVE_TO_OTHER_INVENTORY, HOTBAR_SWAP,
                 HOTBAR_MOVE_AND_READD, SWAP_WITH_CURSOR, CLONE_STACK, UNKNOWN -> {
                if (!topSlotEditable) {
                    e.setCancelled(true);
                    e.setResult(Event.Result.DENY);
                }
            }
            default -> { /* ok */ }
        }

        // Number-key swap into top: allow only if editable
        if (e.getClick() == ClickType.NUMBER_KEY && clickIsTop && !topSlotEditable) {
            e.setCancelled(true);
            e.setResult(Event.Result.DENY);
        }
    }

    // Block drags that touch any non-editable top slot
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        HumanEntity who = e.getWhoClicked();
        if (!(who instanceof Player p)) return;
        if (!isTradeMenuOpen(p)) return;

        Inventory top = p.getOpenInventory().getTopInventory();
        if (top == null) return;
        int size = top.getSize();

        for (int raw : e.getRawSlots()) {
            if (raw < size && isTopNonEditable(p, raw)) {
                e.setCancelled(true);
                e.setResult(Event.Result.DENY);
                return;
            }
        }
    }
}
