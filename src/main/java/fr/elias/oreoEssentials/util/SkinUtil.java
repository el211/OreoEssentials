package fr.elias.oreoEssentials.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import java.lang.reflect.Method;

public final class SkinUtil {
    private SkinUtil() {}

    /** Returns target's live profile (online only) or null if not online. */
    public static PlayerProfile onlineProfileOf(String name) {
        Player p = Bukkit.getPlayerExact(name);
        return p != null ? p.getPlayerProfile() : null;
    }

    /** Try to copy only the skin/cape from source -> dest using PlayerTextures. */
    public static void copyTextures(PlayerProfile source, PlayerProfile dest) {
        if (source == null || dest == null) return;
        try {
            PlayerTextures st = source.getTextures();
            PlayerTextures dt = dest.getTextures();
            if (st == null || dt == null) return;
            // Elytra methods are not in all APIs; copy only skin + cape
            if (st.getSkin() != null) dt.setSkin(st.getSkin());
            if (st.getCape() != null) dt.setCape(st.getCape());
            dest.setTextures(dt);
        } catch (Throwable ignored) {}
    }

    /**
     * Apply a profile to a player.
     * On Paper/Purpur: Player#setPlayerProfile(PlayerProfile) exists.
     * On Spigot: it doesn't – this call will no-op and should be replaced by a disguises/NMS approach.
     */
    public static boolean applyProfile(Player player, PlayerProfile profile) {
        if (player == null || profile == null) return false;
        try {
            Method m = player.getClass().getMethod("setPlayerProfile", PlayerProfile.class);
            m.invoke(player, profile);
            return true;
        } catch (Throwable ignored) {
            return false; // not supported on this platform
        }
    }

    /** Try to set profile name via reflection (Paper has setName). Returns true if applied. */
    public static boolean setProfileName(PlayerProfile profile, String newName) {
        if (profile == null || newName == null) return false;
        try {
            Method m = profile.getClass().getMethod("setName", String.class);
            m.invoke(profile, newName);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
