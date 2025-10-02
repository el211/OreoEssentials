// File: src/main/java/fr/elias/oreoEssentials/services/VanishService.java
package fr.elias.oreoEssentials.services;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VanishService {

    private final Plugin plugin;
    private final Set<UUID> vanished = new HashSet<>();

    public VanishService(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean isVanished(Player p) {
        return vanished.contains(p.getUniqueId());
    }

    /** Toggle vanish for a player. Returns true if now vanished, false if now visible. */
    public boolean toggle(Player p) {
        if (isVanished(p)) {
            show(p);
            vanished.remove(p.getUniqueId());
            return false;
        } else {
            hide(p);
            vanished.add(p.getUniqueId());
            return true;
        }
    }

    /** Hide p from everyone else. */
    public void hide(Player p) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p)) continue;
            other.hidePlayer(plugin, p); // <--- plugin REQUIRED
        }
    }

    /** Show p to everyone else. */
    public void show(Player p) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p)) continue;
            other.showPlayer(plugin, p); // <--- plugin REQUIRED
        }
    }

    /** Ensure joining player cannot see any already-vanished players. */
    public void applyToJoiner(Player joiner) {
        for (UUID id : vanished) {
            Player vanishedPlayer = Bukkit.getPlayer(id);
            if (vanishedPlayer != null && vanishedPlayer.isOnline()) {
                joiner.hidePlayer(plugin, vanishedPlayer);
            }
        }
    }

    /** Make sure a leaving player is removed from the set. */
    public void handleQuit(Player quitter) {
        vanished.remove(quitter.getUniqueId());
    }
}
