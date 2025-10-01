package fr.elias.oreoEssentials.services;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class VanishService {
    private final Set<UUID> vanished = Collections.synchronizedSet(new HashSet<>());
    private Plugin plugin;

    public void init(Plugin plugin) { this.plugin = plugin; }

    public boolean isVanished(UUID id) { return vanished.contains(id); }

    public boolean toggle(Player p) {
        if (isVanished(p.getUniqueId())) {
            show(p);
            return false;
        } else {
            hide(p);
            return true;
        }
    }

    public void hide(Player target) {
        vanished.add(target.getUniqueId());

        // Hide from all who DON'T have bypass permission
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!canSeeVanished(viewer)) {
                viewer.hidePlayer(plugin, target);
            } else {
                viewer.showPlayer(plugin, target);
            }
        }

        // Client-side polish (Spigot-safe)
        target.setInvisible(true);   // client invisibility flag
        target.setCollidable(false);
        target.setSilent(true);
        target.setGlowing(false);

        // If you want to ensure mobs won't target, we handle it in the listener.
        // Do NOT call Paper-only methods here.
        // (No setPotionParticles / setAffectsSpawning in Spigot)

        // Optional (commented): don’t force gamemode changes by default
        // target.setGameMode(GameMode.SPECTATOR);
    }

    public void show(Player target) {
        vanished.remove(target.getUniqueId());

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.showPlayer(plugin, target);
        }

        target.setInvisible(false);
        target.setCollidable(true);
        target.setSilent(false);

        // If you changed it earlier, revert it:
        if (target.getGameMode() == GameMode.SPECTATOR) {
            target.setGameMode(GameMode.SURVIVAL);
        }
    }

    public void applyVisibilityForJoiner(Player joiner) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(joiner)) continue;
            if (isVanished(target.getUniqueId())) {
                if (!canSeeVanished(joiner)) {
                    joiner.hidePlayer(plugin, target);
                } else {
                    joiner.showPlayer(plugin, target);
                }
            }
        }
    }

    public boolean canSeeVanished(Player p) {
        return p.hasPermission("oreo.vanish.see");
    }
}
