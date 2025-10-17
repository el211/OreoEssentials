// File: src/main/java/fr/elias/oreoEssentials/jail/JailService.java
package fr.elias.oreoEssentials.jail;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.TimeText;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class JailService {
    private final Plugin plugin;
    private final JailStorage storage;

    private final Map<String, JailModels.Jail> jails = new HashMap<>();
    private final Map<UUID, JailModels.Sentence> active = new HashMap<>();
    private BukkitTask guardTask;

    // simple command blacklist inside jail (optional)
    private final Set<String> blockedCommands = new HashSet<>(Arrays.asList(
            "spawn","home","sethome","warp","rtp","tpa","tp","back"
    ));

    public JailService(Plugin plugin) {
        this.plugin = plugin;
        this.storage = new YamlJailStorage(plugin);
    }

    public void enable() {
        jails.clear();
        jails.putAll(storage.loadJails());
        active.clear();
        active.putAll(storage.loadSentences());

        if (guardTask != null) guardTask.cancel();
        guardTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.getLogger().info("[Jails] Loaded " + jails.size() + " jails, " + active.size() + " active sentence(s).");
    }

    public void disable() {
        try { storage.saveJails(jails); } catch (Throwable ignored) {}
        try { storage.saveSentences(active); } catch (Throwable ignored) {}
        if (guardTask != null) guardTask.cancel();
        storage.close();
    }

    /* ------------ Admin API ------------ */

    public boolean createOrUpdateJail(String name, JailModels.Cuboid region, String world) {
        name = name.toLowerCase(Locale.ROOT);
        JailModels.Jail j = jails.getOrDefault(name, new JailModels.Jail());
        j.name = name;
        j.world = world;
        j.region = region;
        jails.put(name, j);
        storage.saveJails(jails);
        return true;
    }

    public boolean addCell(String jailName, String cellId, Location loc) {
        JailModels.Jail j = jails.get(jailName.toLowerCase(Locale.ROOT));
        if (j == null) return false;
        j.cells.put(cellId, loc.clone());
        storage.saveJails(jails);
        return true;
    }

    public Map<String, JailModels.Jail> allJails() { return Collections.unmodifiableMap(jails); }

    /* ------------ Sentencing ------------ */

    public boolean jail(UUID player, String jailName, String cellId, long durationMs, String reason, String by) {
        jailName = jailName.toLowerCase(Locale.ROOT);
        JailModels.Jail j = jails.get(jailName);
        if (j == null || !j.isValid()) return false;
        Location spawn = (cellId != null ? j.cells.get(cellId) : null);
        if (spawn == null && !j.cells.isEmpty()) spawn = j.cells.values().iterator().next();
        if (spawn == null) return false;

        JailModels.Sentence s = new JailModels.Sentence();
        s.player = player;
        s.jailName = jailName;
        s.cellId = cellId;
        s.reason = reason == null ? "" : reason;
        s.by = by == null ? "console" : by;
        s.endEpochMs = durationMs <= 0 ? 0 : (System.currentTimeMillis() + durationMs);

        active.put(player, s);
        storage.saveSentences(active);

        Player p = Bukkit.getPlayer(player);
        if (p != null) {
            p.teleport(spawn);
            p.sendMessage("§cYou have been jailed" + (s.endEpochMs > 0 ? (" for " + TimeText.format(durationMs)) : " permanently")
                    + (s.reason.isBlank() ? "" : " §7Reason: §f" + s.reason));
        }

        // ---- ADDED: Discord notify (JAIL) ----
        try {
            if (plugin instanceof OreoEssentials oe) {
                var d = oe.getDiscordMod();
                if (d != null && d.isEnabled()) {
                    String name = String.valueOf(Bukkit.getOfflinePlayer(player).getName());
                    d.notifyJail(name, player, j.name, cellId, s.reason, s.by, s.endEpochMs);
                }
            }
        } catch (Throwable ignored) {}

        return true;
    }

    public boolean release(UUID player) {
        JailModels.Sentence s = active.remove(player);
        storage.saveSentences(active);
        if (s != null) {
            Player p = Bukkit.getPlayer(player);
            if (p != null) p.sendMessage("§aYou have been released from jail.");

            // ---- ADDED: Discord notify (UNJAIL) ----
            try {
                if (plugin instanceof OreoEssentials oe) {
                    var d = oe.getDiscordMod();
                    if (d != null && d.isEnabled()) {
                        String name = String.valueOf(Bukkit.getOfflinePlayer(player).getName());
                        d.notifyUnjail(name, player, s.by == null || s.by.isBlank() ? "system" : s.by);
                    }
                }
            } catch (Throwable ignored) {}

            return true;
        }
        return false;
    }

    public JailModels.Sentence sentence(UUID u) { return active.get(u); }

    /* ------------ Enforcement ------------ */

    private void tick() {
        boolean changed = false;

        // expire
        Iterator<Map.Entry<UUID, JailModels.Sentence>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, JailModels.Sentence> e = it.next();
            if (e.getValue().expired()) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) p.sendMessage("§aYour jail time is over.");
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            try { storage.saveSentences(active); } catch (Throwable ignored) {}
        }

        // keep inmates inside
        for (Map.Entry<UUID, JailModels.Sentence> e : active.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            JailModels.Jail j = jails.get(e.getValue().jailName);
            if (j == null || !j.isValid()) continue;
            if (!p.getWorld().getName().equalsIgnoreCase(j.world) || !j.region.contains(p.getLocation())) {
                Location spawn = j.cells.getOrDefault(e.getValue().cellId,
                        j.cells.isEmpty() ? p.getLocation() : j.cells.values().iterator().next());
                if (spawn != null) p.teleport(spawn);
            }
        }
    }

    public boolean isCommandBlockedFor(Player p, String baseCmd) {
        JailModels.Sentence s = active.get(p.getUniqueId());
        return s != null && blockedCommands.contains(baseCmd.toLowerCase(Locale.ROOT));
    }
}
