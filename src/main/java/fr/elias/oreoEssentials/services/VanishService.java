// File: src/main/java/fr/elias/oreoEssentials/services/VanishService.java
package fr.elias.oreoEssentials.services;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VanishService {
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public boolean isVanished(UUID id) {
        return vanished.contains(id);
    }

    public void setVanished(Plugin plugin, Player p, boolean vanish) {
        if (vanish) {
            if (vanished.add(p.getUniqueId())) {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.equals(p)) other.hidePlayer(plugin, p);
                }
            }
        } else {
            if (vanished.remove(p.getUniqueId())) {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.equals(p)) other.showPlayer(plugin, p);
                }
            }
        }
    }

    /** Ensure a joining player can’t see any vanished players. */
    public void applyToJoiner(Plugin plugin, Player joiner) {
        for (UUID id : vanished) {
            Player v = Bukkit.getPlayer(id);
            if (v != null && v.isOnline() && !v.equals(joiner)) {
                joiner.hidePlayer(plugin, v);
            }
        }
    }
}
