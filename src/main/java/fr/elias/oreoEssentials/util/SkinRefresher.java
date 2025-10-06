// src/main/java/fr/elias/oreoEssentials/util/SkinRefresher.java
package fr.elias.oreoEssentials.util;

import org.bukkit.entity.Player;

/** Strategy interface for forcing clients to refresh a player's skin/name. */
public interface SkinRefresher {
    void refresh(Player player);

    /** Static holder so callers can do SkinRefresher.Holder.refresh(p); */
    final class Holder {
        private static SkinRefresher IMPL = new SkinRefresherFallback();

        private Holder() {}

        public static void set(SkinRefresher impl) {
            if (impl != null) IMPL = impl;
        }
        public static SkinRefresher get() { return IMPL; }
        public static void refresh(Player p) { IMPL.refresh(p); }
    }
}
