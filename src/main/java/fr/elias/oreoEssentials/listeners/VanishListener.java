package fr.elias.oreoEssentials.listeners;

import fr.elias.oreoEssentials.services.VanishService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class VanishListener implements Listener {
    private final VanishService service;

    public VanishListener(VanishService service, org.bukkit.plugin.Plugin plugin) {
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Hide all vanished players from the joiner
        service.applyVisibilityForJoiner(e.getPlayer());
    }

    // Spigot-compatible: use EntityPickupItemEvent and filter to Player
    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (service.isVanished(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onTarget(EntityTargetEvent e) {
        if (!(e.getTarget() instanceof Player p)) return;
        if (service.isVanished(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        if (service.isVanished(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (service.isVanished(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (service.isVanished(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }
}
