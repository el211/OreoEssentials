// File: src/main/java/fr/elias/oreoEssentials/trade/TradeView.java
package fr.elias.oreoEssentials.trade;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.trade.ui.TradeMenu;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.PluginManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guard layer for Trade GUI that enforces:
 * - A can edit only A-area, B can edit only B-area.
 * - Opponent area is always read-only.
 * - Blocks shift-clicks, hotbar swaps, drags into top, collect-to-cursor, etc.
 *
 * No modifications to SmartInvs are needed.
 */
public final class TradeView {

    private static final int[] A_AREA_SLOTS = rectSlots(2, 2, 3, 3); // rows 2..4, cols 2..4
    private static final int[] B_AREA_SLOTS = rectSlots(2, 6, 3, 3); // rows 2..4, cols 6..8

    private static final Set<Integer> A_ALLOWED = toSet(A_AREA_SLOTS);
    private static final Set<Integer> B_ALLOWED = toSet(B_AREA_SLOTS);

    // Which TradeSession a player is viewing, and which side they are (true = A, false = B)
    private static final Map<UUID, TradeSession> VIEW_SESSION = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> VIEW_SIDE_A = new ConcurrentHashMap<>();

    private static volatile boolean LISTENER_REGISTERED = false;

    private TradeView() {}

    /** Call this after opening the SmartInvs menu for a viewer. */
    public static void registerGuards(OreoEssentials plugin, TradeConfig cfg, TradeSession session, Player viewer, boolean forA) {
        if (viewer == null) return;
        VIEW_SESSION.put(viewer.getUniqueId(), session);
        VIEW_SIDE_A.put(viewer.getUniqueId(), forA);
        ensureListener(plugin);
    }

    /** Optional helper to clear when you explicitly close. */
    public static void unregisterViewer(Player p) {
        if (p != null) {
            VIEW_SESSION.remove(p.getUniqueId());
            VIEW_SIDE_A.remove(p.getUniqueId());
        }
    }

    private static void ensureListener(OreoEssentials plugin) {
        if (LISTENER_REGISTERED) return;
        synchronized (TradeView.class) {
            if (LISTENER_REGISTERED) return;
            PluginManager pm = plugin.getServer().getPluginManager();
            pm.registerEvents(new GuardListener(plugin), plugin);
            LISTENER_REGISTERED = true;
        }
    }

    /* -------------------- internals -------------------- */

    private static boolean isTradeTop(OreoEssentials plugin, Player p) {
        // Detect if the player has our TradeMenu open via SmartInvs
        try {
            Optional<SmartInventory> inv = plugin.getInvManager().getInventory(p);
            return inv.isPresent() && (inv.get().getProvider() instanceof TradeMenu);
        } catch (Throwable ignored) { }
        return false;
    }

    private static boolean isAllowedSlot(UUID pid, int rawTopSlot) {
        Boolean forA = VIEW_SIDE_A.get(pid);
        if (forA == null) return false;
        return forA ? A_ALLOWED.contains(rawTopSlot) : B_ALLOWED.contains(rawTopSlot);
    }

    private static int rawTopSlot(int row, int col) { return row * 9 + col; }

    private static int[] rectSlots(int startRow, int startCol, int height, int width) {
        int[] out = new int[height * width];
        int k = 0;
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                out[k++] = rawTopSlot(startRow + r, startCol + c);
            }
        }
        return out;
    }

    private static Set<Integer> toSet(int[] arr) {
        Set<Integer> s = new HashSet<>(arr.length * 2);
        for (int v : arr) s.add(v);
        return Collections.unmodifiableSet(s);
    }

    /* -------------------- listener -------------------- */

    private static final class GuardListener implements Listener {
        private final OreoEssentials plugin;

        GuardListener(OreoEssentials plugin) { this.plugin = plugin; }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onClick(InventoryClickEvent e) {
            if (!(e.getWhoClicked() instanceof Player)) return;
            Player p = (Player) e.getWhoClicked();

            // Not in our trade GUI? ignore.
            if (!VIEW_SESSION.containsKey(p.getUniqueId())) return;
            if (!isTradeTop(plugin, p)) { // closed or switched GUI -> clean mapping
                unregisterViewer(p);
                return;
            }

            // Always block actions that can move items into/out of top
            switch (e.getAction()) {
                case COLLECT_TO_CURSOR:
                case MOVE_TO_OTHER_INVENTORY:
                case HOTBAR_SWAP:
                case HOTBAR_MOVE_AND_READD:
                case SWAP_WITH_CURSOR:
                case UNKNOWN:
                    e.setCancelled(true);
                    return;
                default: /* continue */ break;
            }

            // Click on top inventory?
            Inventory top = p.getOpenInventory().getTopInventory();
            if (e.getClickedInventory() == top) {
                int raw = e.getSlot(); // raw within top
                // Allow ONLY if the slot is in the editor's allowed 3x3
                if (!isAllowedSlot(p.getUniqueId(), raw)) {
                    e.setCancelled(true);
                    return;
                }
            } else {
                // Bottom inv click with shift that would move to top — already handled by MOVE_TO_OTHER_INVENTORY above
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onDrag(InventoryDragEvent e) {
            if (!(e.getWhoClicked() instanceof Player)) return;
            Player p = (Player) e.getWhoClicked();

            if (!VIEW_SESSION.containsKey(p.getUniqueId())) return;
            if (!isTradeTop(plugin, p)) { unregisterViewer(p); return; }

            Inventory top = p.getOpenInventory().getTopInventory();
            // If any dragged slot touches top and is not allowed, cancel
            for (int raw : e.getRawSlots()) {
                if (raw < top.getSize()) { // it's in the top
                    if (!isAllowedSlot(p.getUniqueId(), raw)) {
                        e.setCancelled(true);
                        return;
                    }
                }
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onClose(InventoryCloseEvent e) {
            if (!(e.getPlayer() instanceof Player)) return;
            Player p = (Player) e.getPlayer();
            // If they closed the trade GUI, clear mapping
            if (VIEW_SESSION.containsKey(p.getUniqueId()) && !isTradeTop(plugin, p)) {
                unregisterViewer(p);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuit(PlayerQuitEvent e) {
            unregisterViewer(e.getPlayer());
        }
    }
}
