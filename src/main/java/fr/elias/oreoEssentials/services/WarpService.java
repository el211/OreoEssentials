// File: src/main/java/fr/elias/oreoEssentials/services/WarpService.java
package fr.elias.oreoEssentials.services;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;

public class WarpService {

    private final StorageApi storage;
    private final WarpDirectory directory; // optional (null when not using Mongo directory)

    /** Backward-compatible ctor (no directory). */
    public WarpService(StorageApi storage) {
        this(storage, null);
    }

    /** Preferred ctor when you have a cross-server WarpDirectory. */
    public WarpService(StorageApi storage, WarpDirectory directory) {
        this.storage = storage;
        this.directory = directory;
    }

    /* ---------------- basic warp ops (delegate to StorageApi) ---------------- */

    public boolean setWarp(String name, Location loc) {
        storage.setWarp(name, loc);
        return true;
    }

    public boolean delWarp(String name) {
        boolean ok = storage.delWarp(name);
        if (ok && directory != null) {
            try { directory.deleteWarp(name); } catch (Throwable ignored) {}
        }
        return ok;
    }

    public Location getWarp(String name) {
        return storage.getWarp(name);
    }

    public Set<String> listWarps() {
        return storage.listWarps();
    }

    /* ---------------- permission helpers (directory-backed) ---------------- */

    /** Returns the required permission for this warp, or null if public/no requirement. */
    public String requiredPermission(String warp) {
        return (directory == null ? null : directory.getWarpPermission(warp));
    }

    /** Convenience: true if the warp is public OR the player has the required permission. */
    public boolean canUse(Player p, String warp) {
        String perm = requiredPermission(warp);
        return (perm == null || perm.isBlank()) || p.hasPermission(perm);
    }

    /** Read the current permission string. */
    public String getWarpPermission(String warp) {
        return (directory == null ? null : directory.getWarpPermission(warp));
    }

    /**
     * Set or clear a per-warp permission.
     * Passing null/blank clears the permission (warp becomes public).
     */
    public void setWarpPermission(String warp, String permission) {
        if (directory == null) return;
        directory.setWarpPermission(warp, permission);
    }

    /* ---------------- rename helper (storage-agnostic) ---------------- */

    /**
     * Best-effort rename that keeps location. Fails if old is missing or new already exists.
     */
    public boolean renameWarp(String oldName, String newName) {
        if (oldName == null || newName == null) return false;
        oldName = oldName.trim().toLowerCase(Locale.ROOT);
        newName = newName.trim().toLowerCase(Locale.ROOT);
        if (oldName.isEmpty() || newName.isEmpty() || oldName.equals(newName)) return false;

        Location loc = storage.getWarp(oldName);
        if (loc == null) return false;                   // nothing to rename
        if (storage.getWarp(newName) != null) return false; // don’t overwrite

        storage.setWarp(newName, loc);
        boolean delOk = storage.delWarp(oldName);
        if (!delOk) {
            // rollback best-effort
            storage.delWarp(newName);
            return false;
        }

        // move directory metadata too (if available)
        if (directory != null) {
            try {
                String server = directory.getWarpServer(oldName);
                String perm   = directory.getWarpPermission(oldName);
                if (server != null) directory.setWarpServer(newName, server);
                if (perm != null && !perm.isBlank()) directory.setWarpPermission(newName, perm);
                directory.deleteWarp(oldName);
            } catch (Throwable ignored) {}
        }

        return true;
    }
}
