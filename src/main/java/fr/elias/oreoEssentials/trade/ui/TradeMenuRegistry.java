// File: src/main/java/fr/elias/oreoEssentials/trade/ui/TradeMenuRegistry.java
package fr.elias.oreoEssentials.trade.ui;

import fr.elias.oreoEssentials.trade.TradeSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class TradeMenuRegistry {
    private final ConcurrentMap<UUID, TradeMenu> byViewer = new ConcurrentHashMap<>();

    /** Register the active TradeMenu instance for a viewer UUID. */
    public void register(UUID viewerId, TradeMenu menu) {
        if (viewerId == null || menu == null) return;
        byViewer.put(viewerId, menu);
    }

    /** Remove the viewer -> menu mapping (call on InventoryClose). */
    public void unregister(UUID viewerId) {
        if (viewerId == null) return;
        byViewer.remove(viewerId);
    }

    /** Get (and allow modifying) the mapping for a viewer. */
    public TradeMenu get(UUID viewerId) {
        if (viewerId == null) return null;
        return byViewer.get(viewerId);
    }

    /** Non-destructive lookup (alias of get). */
    public TradeMenu peek(UUID viewer) {
        if (viewer == null) return null;
        return byViewer.get(viewer);
    }

    /** Ask the viewer's menu (if any) to repaint from the session. */
    public void refreshViewer(UUID viewerId) {
        TradeMenu m = get(viewerId);
        if (m == null) return;
        // Run on main thread to be safe for inventory ops
        try {
            Bukkit.getScheduler().runTask(m.getPlugin(), m::refreshFromSession);
        } catch (Throwable ignored) {}
    }

    /**
     * Defensive helper: if the viewer has no open trade menu, create and open one
     * for the provided session. This matches how TradeService now calls it.
     */
    public void ensureOpen(UUID viewerId, TradeSession session) {
        if (viewerId == null || session == null) return;

        TradeMenu existing = byViewer.get(viewerId);
        if (existing != null && existing.isOpenFor(viewerId)) {
            return; // already open
        }

        // Build (or re-build) a menu from the session
        TradeMenu newMenu = TradeMenu.createForSession(session);
        if (newMenu == null) return;

        byViewer.put(viewerId, newMenu);

        Player p = Bukkit.getPlayer(viewerId);
        if (p != null && p.isOnline()) {
            newMenu.openFor(viewerId);
        }
    }


    /** Blow away all references (e.g., on plugin disable). */
    public void closeAll() {
        byViewer.clear();
    }
}
