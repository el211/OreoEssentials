package fr.elias.oreoEssentials.offineplayers;

import javax.annotation.Nullable;
import java.util.UUID;

// SnapshotStorage.java – interface (so you can swap to Mongo later)
public interface SnapshotStorage {
    @Nullable
    InvSnapshot   loadInv(UUID uuid);
    void                    saveInv(UUID uuid, InvSnapshot snap);
    @Nullable EnderSnapshot loadEnder(UUID uuid);
    void                    saveEnder(UUID uuid, EnderSnapshot snap);

    // “pending edits” applied on next join for offline targets:
    @Nullable InvSnapshot   loadPendingInv(UUID uuid);
    void                    savePendingInv(UUID uuid, InvSnapshot snap);
    void                    clearPendingInv(UUID uuid);

    @Nullable EnderSnapshot loadPendingEnder(UUID uuid);
    void                    savePendingEnder(UUID uuid, EnderSnapshot snap);
    void                    clearPendingEnder(UUID uuid);
}

