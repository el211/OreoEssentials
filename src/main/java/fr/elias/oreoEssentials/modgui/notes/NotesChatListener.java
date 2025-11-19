// package: fr.elias.oreoEssentials.modgui.notes
package fr.elias.oreoEssentials.modgui.notes;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NotesChatListener implements Listener {

    private final OreoEssentials plugin;
    private final PlayerNotesManager manager;

    // staff -> target
    private final Map<UUID, UUID> pending = new ConcurrentHashMap<>();

    public NotesChatListener(OreoEssentials plugin, PlayerNotesManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void startNote(Player staff, UUID target) {
        pending.put(staff.getUniqueId(), target);
        staff.sendMessage("§eType the note in chat. §7(Or type 'cancel' to abort)");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent e) {
        UUID staffId = e.getPlayer().getUniqueId();
        if (!pending.containsKey(staffId)) return;

        e.setCancelled(true);
        UUID target = pending.remove(staffId);
        String msg = e.getMessage();
        if (msg.equalsIgnoreCase("cancel")) {
            e.getPlayer().sendMessage("§cNote cancelled.");
            return;
        }
        manager.addNote(target, e.getPlayer().getName(), msg);
        e.getPlayer().sendMessage("§aNote added.");
    }
}
