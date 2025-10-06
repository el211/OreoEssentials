// src/main/java/fr/elias/oreoEssentials/util/SkinHookSR.java
package fr.elias.oreoEssentials.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class SkinHookSR {
    private SkinHookSR() {}

    public static boolean isPresent() {
        return Bukkit.getPluginManager().getPlugin("SkinsRestorer") != null
                || Bukkit.getPluginManager().getPlugin("SkinsRestorerX") != null;
    }

    /** Try to apply a skin by player name via SkinsRestorer. Returns true if invoked OK. */
    public static boolean applySkinByName(Player player, String skinName) {
        try {
            // API: net.skinsrestorer.api.SkinsRestorerProvider.get().getSkinApplier(player).applySkin(player, skinName);
            Class<?> provider = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
            Object api = provider.getMethod("get").invoke(null);
            Object applier = api.getClass().getMethod("getSkinApplier", Player.class).invoke(api, player);
            applier.getClass().getMethod("applySkin", Player.class, String.class).invoke(applier, player, skinName);
            return true;
        } catch (Throwable ignored) { return false; }
    }
}
