package fr.elias.oreoEssentials.playtime;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-server playtime (in seconds) for each player.
 * Persists to plugins/OreoEssentials/playtime_data.yml
 */
public final class PlaytimeTracker implements Listener {

    private final OreoEssentials plugin;

    /** Accumulated total seconds per UUID (persisted) */
    private final Map<UUID, Long> totals = new ConcurrentHashMap<>();

    /** Join timestamp (millis) per online player */
    private final Map<UUID, Long> onlineSince = new ConcurrentHashMap<>();

    /** One-time set of players we’ve baselined from bukkit (so we don’t do it twice) */
    private final Set<UUID> baselined = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final File file;
    private YamlConfiguration yaml;

    private int autosaveTask = -1;

    public PlaytimeTracker(OreoEssentials plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "playtime_data.yml");
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // periodic autosave so crashes don't lose much
        autosaveTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::saveQuiet, 20L * 60, 20L * 60);
    }

    public void shutdown() {
        if (autosaveTask != -1) {
            Bukkit.getScheduler().cancelTask(autosaveTask);
            autosaveTask = -1;
        }
        flushOnlineDeltas();
        saveQuiet();
        HandlerList.unregisterAll(this);
    }

    /* -------------------- Persistence -------------------- */

    private void load() {
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                yaml = new YamlConfiguration();
                yaml.save(file);
            }
            yaml = YamlConfiguration.loadConfiguration(file);

            // read totals
            if (yaml.isConfigurationSection("totals")) {
                for (String k : yaml.getConfigurationSection("totals").getKeys(false)) {
                    try {
                        UUID u = UUID.fromString(k);
                        totals.put(u, yaml.getLong("totals." + k, 0L));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            // baselined set
            baselined.clear();
            for (String s : yaml.getStringList("baselined")) {
                try { baselined.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
            }

        } catch (Exception e) {
            plugin.getLogger().warning("[PlaytimeTracker] Failed to load: " + e.getMessage());
            yaml = new YamlConfiguration();
        }
    }

    private void saveQuiet() {
        try {
            YamlConfiguration out = new YamlConfiguration();
            // flush any currently online deltas to in-memory totals first
            flushOnlineDeltas();
            for (Map.Entry<UUID, Long> e : totals.entrySet()) {
                out.set("totals." + e.getKey(), e.getValue());
            }
            List<String> bl = new ArrayList<>();
            for (UUID u : baselined) bl.add(u.toString());
            out.set("baselined", bl);
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[PlaytimeTracker] Save failed: " + e.getMessage());
        }
    }

    private void flushOnlineDeltas() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> e : onlineSince.entrySet()) {
            UUID u = e.getKey();
            long start = e.getValue();
            long addSec = Math.max(0, (now - start) / 1000L);
            totals.merge(u, addSec, Long::sum);
            onlineSince.put(u, now); // reset baseline to now
        }
    }

    /* -------------------- API -------------------- */

    /** Returns per-server seconds for this player (includes current session). */
    public long getSeconds(UUID uuid) {
        long base = totals.getOrDefault(uuid, 0L);
        Long start = onlineSince.get(uuid);
        if (start != null) {
            base += Math.max(0, (System.currentTimeMillis() - start) / 1000L);
        }
        return Math.max(0, base);
    }

    /** One-time baseline from Bukkit vanilla seconds for this UUID. */
    public void baselineFromBukkitIfNeeded(Player p) {
        if (baselined.contains(p.getUniqueId())) return;
        baselined.add(p.getUniqueId());
        // set internal total to current vanilla seconds (acts as a baseline, so no retro payouts later)
        int ticks = p.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
        long sec = Math.max(0, ticks / 20L);
        totals.put(p.getUniqueId(), sec);
    }

    /* -------------------- Events -------------------- */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        onlineSince.put(p.getUniqueId(), System.currentTimeMillis());

        // optional baseline
        boolean baseline = plugin.getConfig().getBoolean("playtime.internal.baseline-from-bukkit-on-first-seen",
                false); // or read from playtime_rewards.yml if you prefer
        if (baseline) baselineFromBukkitIfNeeded(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Long start = onlineSince.remove(p.getUniqueId());
        if (start != null) {
            long addSec = Math.max(0, (System.currentTimeMillis() - start) / 1000L);
            totals.merge(p.getUniqueId(), addSec, Long::sum);
        }
        saveQuiet();
    }
}
