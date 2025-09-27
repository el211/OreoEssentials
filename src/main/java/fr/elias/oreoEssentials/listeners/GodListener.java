package fr.elias.oreoEssentials.listeners;

import fr.elias.oreoEssentials.services.GodService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class GodListener implements Listener {
    private final GodService god;

    public GodListener(GodService god) {
        this.god = god;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!god.isGod(p.getUniqueId())) return;

        // Cancel *all* damage while in god
        e.setCancelled(true);

        // QoL: clear fire ticks on any damage tick that would have happened
        p.setFireTicks(0);
        // Top up health if needed (optional)
        if (p.getHealth() < p.getMaxHealth()) {
            p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + 0.5)); // small regen each “would-be” tick
        }

        // Extra safety for some damage types
        DamageCause c = e.getCause();
        if (c == DamageCause.VOID) {
            // bounce them up a little if falling into the void
            p.teleport(p.getLocation().add(0, 2, 0));
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!god.isGod(p.getUniqueId())) return;

        e.setCancelled(true);
        e.setFoodLevel(20);
        p.setSaturation(20f);
        p.setExhaustion(0f);
    }

    @EventHandler
    public void onAirChange(EntityAirChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!god.isGod(p.getUniqueId())) return;

        // Prevent drowning
        e.setAmount(p.getMaximumAir());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!god.isGod(p.getUniqueId())) return;

        // Keep them not-on-fire while moving
        if (p.getFireTicks() > 0) p.setFireTicks(0);
    }
}
