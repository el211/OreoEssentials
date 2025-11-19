// package: fr.elias.oreoEssentials.modgui.freeze
package fr.elias.oreoEssentials.modgui.freeze;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import fr.elias.oreoEssentials.util.Lang;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FreezeManager {

    public static class FreezeData {
        public final UUID target;
        public final UUID staff;
        public final long until; // millis

        public FreezeData(UUID target, UUID staff, long until) {
            this.target = target;
            this.staff  = staff;
            this.until  = until;
        }

        public long remainingMillis() {
            return until - System.currentTimeMillis();
        }
    }

    private final OreoEssentials plugin;
    private final Map<UUID, FreezeData> frozen = new ConcurrentHashMap<>();

    public FreezeManager(OreoEssentials plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    public boolean isFrozen(UUID id) {
        FreezeData data = frozen.get(id);
        if (data == null) return false;
        if (data.remainingMillis() <= 0) {
            frozen.remove(id);
            return false;
        }
        return true;
    }

    public FreezeData get(UUID id) { return frozen.get(id); }

    public void freeze(Player target, Player staff, long seconds) {
        long until = System.currentTimeMillis() + (seconds * 1000L);
        frozen.put(target.getUniqueId(),
                new FreezeData(target.getUniqueId(),
                        staff == null ? null : staff.getUniqueId(),
                        until));
        Lang.send(target, "freeze.frozen", Map.of("seconds", Long.toString(seconds)), target);
    }

    public void unfreeze(Player target) {
        frozen.remove(target.getUniqueId());
        Lang.send(target, "freeze.unfrozen", null, target);
    }

    private void startTickTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();

            // clean & particles + actionbars
            for (Iterator<Map.Entry<UUID, FreezeData>> it = frozen.entrySet().iterator(); it.hasNext();) {
                Map.Entry<UUID, FreezeData> e = it.next();
                FreezeData data = e.getValue();
                if (data.until <= now) {
                    Player t = Bukkit.getPlayer(e.getKey());
                    if (t != null) Lang.send(t, "freeze.expired", null, t);
                    it.remove();
                    continue;
                }

                Player t = Bukkit.getPlayer(e.getKey());
                if (t == null) continue;

                // particle trail
                t.getWorld().spawnParticle(
                        org.bukkit.Particle.SNOWFLAKE,
                        t.getLocation().add(0, 0.1, 0),
                        6, 0.3, 0.1, 0.3, 0.01
                );

                // actionbar to staff (if online)
                long rem = data.remainingMillis() / 1000L;
                String msg = Lang.get("freeze.actionbar-staff", "&c[Frozen] &f%target% &7(%seconds%s)")
                        .replace("%target%", t.getName())
                        .replace("%seconds%", Long.toString(rem));
                if (data.staff != null) {
                    Player s = Bukkit.getPlayer(data.staff);
                    if (s != null) s.spigot().sendMessage(
                            net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            new net.md_5.bungee.api.chat.TextComponent(msg)
                    );
                }
            }
        }, 10L, 10L);
    }
}
