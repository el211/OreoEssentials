// File: src/main/java/fr/elias/oreoEssentials/listeners/VanishListener.java
package fr.elias.oreoEssentials.listeners;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.services.VanishService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class VanishListener implements Listener {
    private final VanishService service;
    private final Plugin plugin;

    public VanishListener(VanishService service, Plugin plugin) {
        this.service = service;
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Hide all vanished players from this joiner
        service.applyToJoiner(plugin, e.getPlayer());
    }

    @EventHandler
    public void onMobTarget(EntityTargetEvent e) {
        if (!(e.getTarget() instanceof Player p)) return;
        if (service.isVanished(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (service.isVanished(p.getUniqueId())) e.setCancelled(true);
    }
}
