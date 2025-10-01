package fr.elias.oreoEssentials.listeners;

import fr.elias.oreoEssentials.services.MuteService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class MuteListener implements Listener {
    private final MuteService mutes;

    public MuteListener(MuteService mutes) {
        this.mutes = mutes;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        var md = mutes.get(p.getUniqueId());
        if (md == null) return;

        e.setCancelled(true);
        long rem = md.remainingMs();
        String remainStr = rem < 0 ? "permanent" : MuteService.friendlyRemaining(rem);
        p.sendMessage(ChatColor.RED + "You are muted"
                + ChatColor.GRAY + " (" + remainStr + ")"
                + (md.reason.isEmpty() ? "" : ChatColor.GRAY + " Reason: " + ChatColor.YELLOW + md.reason));
    }
}
