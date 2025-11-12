// File: src/main/java/fr/elias/oreoEssentials/trade/pending/PendingGrantsDao.java
package fr.elias.oreoEssentials.trade.pending;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Storage for offline trade grants.
 * Provide a Mongo/Redis/YAML implementation in production.
 */
public interface PendingGrantsDao {

    /** Store (overwrite or append) pending items for a target player for a given session. */
    void storePending(UUID target, UUID sessionId, ItemStack[] items);

    /**
     * Fetch all pending items for the player (across sessions if you decide to aggregate),
     * then delete them so they are delivered once.
     * Return null if none.
     */
    PendingItems fetchAndDelete(UUID target);

    /** Simple record wrapper. */
    final class PendingItems {
        public final UUID sessionId; // optional
        public final ItemStack[] items;

        public PendingItems(UUID sessionId, ItemStack[] items) {
            this.sessionId = sessionId;
            this.items = (items == null ? new ItemStack[0] : items);
        }
    }
}
