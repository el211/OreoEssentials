package fr.elias.oreoEssentials.playtime;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public final class PlaytimeRewardsService {

    /* ---------------- Source (where playtime comes from) ---------------- */
    public enum Source { BUKKIT, INTERNAL }

    /* ---------------- GUI skin ---------------- */

    public static final class GuiSkin {
        public String title = "&bPlaytime Rewards";
        public int rows = 5;
        public boolean fillEmpty = true;
        public Material fillerMat = Material.GRAY_STAINED_GLASS_PANE;
        public String fillerName = "&7";
        public int fillerCmd = 0;
        public Map<String, SkinState> states = new HashMap<>();
    }

    public static final class SkinState {
        public Material mat;
        public String name;
        public boolean glow;
    }

    /* ---------------- Fields ---------------- */

    private final OreoEssentials plugin;
    private final PlaytimeTracker tracker; // nullable when using BUKKIT
    final PlaytimeDataStore store;
    private final DirectiveRunner runner;

    private FileConfiguration cfg;
    public final Map<String, RewardEntry> rewards = new LinkedHashMap<>();
    public final GuiSkin skin = new GuiSkin();

    // Settings
    private boolean enabled = true;
    public int notifyEveryMinutes = 10;
    public boolean stackRewards = true;

    // Source selection & baselines
    private Source source = Source.BUKKIT;
    /** One-time: initialize internal counter from Bukkit time on first sighting */
    private boolean baselineFromBukkit = false;
    /** One-time: set repeating reward counters to “already paid up to current time” on first sighting (prevents retro mass payouts) */
    public boolean baselineOnFirstSeen = true;

    private BukkitTask periodicTask;
    private PrewardsListeners listener;

    /* ---------------- Lifecycle ---------------- */

    public PlaytimeRewardsService(OreoEssentials plugin, PlaytimeTracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
        this.store = new YamlPlaytimeDataStore(plugin);
        this.runner = new DirectiveRunner(plugin);
    }

    /** Expose plugin for SmartInvs manager, etc. */
    public OreoEssentials getPlugin() { return plugin; }

    /** Call once from onEnable() */
    public void init() {
        loadConfig();
        if (enabled) start();
        else plugin.getLogger().info("[Prewards] Disabled by config (settings.enable=false).");
    }

    /** Reload file, then (re)apply enabled state */
    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "playtime_rewards.yml");
        if (!file.exists()) plugin.saveResource("playtime_rewards.yml", false);
        cfg = YamlConfiguration.loadConfiguration(file);

        enabled              = cfg.getBoolean("settings.enable", true);
        notifyEveryMinutes   = cfg.getInt("settings.notify-every-minutes", 10);
        stackRewards         = cfg.getBoolean("settings.stack-rewards", true);

        // Source switch (bukkit | internal)
        String srcStr = cfg.getString("settings.source",
                cfg.getBoolean("settings.use-bukkit-statistic", true) ? "bukkit" : "internal");
        source = "internal".equalsIgnoreCase(srcStr) ? Source.INTERNAL : Source.BUKKIT;

        baselineFromBukkit   = cfg.getBoolean("settings.baseline-from-bukkit-on-first-seen", false);
        baselineOnFirstSeen  = cfg.getBoolean("settings.baseline-on-first-seen", true);

        // ----- skin -----
        skin.title     = cfg.getString("settings.gui.title", "&bPlaytime Rewards");
        skin.rows      = Math.max(1, Math.min(6, cfg.getInt("settings.gui.rows", 5)));
        skin.fillEmpty = cfg.getBoolean("settings.gui.fill-empty", true);
        skin.fillerMat = safeMat(cfg.getString("settings.gui.filler.material", "GRAY_STAINED_GLASS_PANE"),
                Material.GRAY_STAINED_GLASS_PANE);
        skin.fillerName = cfg.getString("settings.gui.filler.name", "&7");
        skin.fillerCmd  = cfg.getInt("settings.gui.filler.custom-model-data", 0);
        skin.states.clear();
        ConfigurationSection st = cfg.getConfigurationSection("settings.gui.states");
        if (st != null) {
            for (String k : st.getKeys(false)) {
                SkinState s = new SkinState();
                s.mat  = safeMat(st.getString(k + ".material", "PAPER"), Material.PAPER);
                s.name = st.getString(k + ".name", k);
                s.glow = st.getBoolean(k + ".glow", false);
                skin.states.put(k.toUpperCase(Locale.ROOT), s);
            }
        }

        // ----- rewards -----
        rewards.clear();
        ConfigurationSection rs = cfg.getConfigurationSection("rewards");
        if (rs != null) {
            for (String id : rs.getKeys(false)) {
                String dn = rs.getString(id + ".DisplayName", id);
                boolean ac = rs.getBoolean(id + ".AutoClaim", false);
                List<String> desc = rs.getStringList(id + ".Description");

                Long payFor   = rs.isSet(id + ".PayFor")   ? rs.getLong(id + ".PayFor")   : null;
                Long payEvery = rs.isSet(id + ".PayEvery") ? rs.getLong(id + ".PayEvery") : null;

                boolean rewardStack   = rs.getBoolean(id + ".StackRewards", true);
                boolean requiresPerm  = rs.getBoolean(id + ".requires-permission", false);

                List<String> cmds = rs.getStringList(id + ".Commands");

                Integer slot = rs.isSet(id + ".gui.slot") ? rs.getInt(id + ".gui.slot") : null;
                Material mat = safeMat(rs.getString(id + ".gui.icon.material", "PAPER"), Material.PAPER);
                Integer cmd  = rs.isSet(id + ".gui.icon.custom-model-data") ? rs.getInt(id + ".gui.icon.custom-model-data") : null;
                String name  = rs.getString(id + ".gui.icon.name");
                List<String> lore = rs.getStringList(id + ".gui.icon.lore");

                rewards.put(id, new RewardEntry(
                        id, dn, ac, desc,
                        payFor, payEvery,
                        rewardStack, requiresPerm,
                        cmds, slot, mat, cmd, name, lore
                ));
            }
        }

        // Apply enable toggle live
        if (enabled && (periodicTask == null && listener == null)) start();
        if (!enabled) stop();
    }

    private static Material safeMat(String name, Material def) {
        try {
            Material m = Material.matchMaterial(name);
            return (m != null) ? m : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private void start() {
        stop(); // safety
        // Periodic checker (every 30s)
        periodicTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) checkPlayer(p, true);
        }, 20L, 20L * 30);

        // Join reminder listener (per-player timers inside listener)
        listener = new PrewardsListeners(plugin, this);
        Bukkit.getPluginManager().registerEvents(listener, plugin);

        plugin.getLogger().info("[Prewards] Enabled.");
    }

    private void stop() {
        if (periodicTask != null) {
            periodicTask.cancel();
            periodicTask = null;
        }
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
    }

    public boolean isEnabled() { return enabled; }

    /* ---------------- Core logic ---------------- */

    /** Returns playtime (seconds) based on the configured source. */
    public long getPlaytimeSeconds(Player p) {
        if (source == Source.INTERNAL && tracker != null) {
            if (baselineFromBukkit) {
                // one-time: initialize internal counter from vanilla on first sighting
                tracker.baselineFromBukkitIfNeeded(p);
            }
            return tracker.getSeconds(p.getUniqueId());
        }
        // default: vanilla lifetime (PLAY_ONE_MINUTE is in ticks / 20)
        int ticks = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
        return Math.max(0, ticks / 20L);
    }

    public enum State { LOCKED, READY, CLAIMED, REPEATING }

    public State stateOf(Player p, RewardEntry r) {
        long secs = getPlaytimeSeconds(p);
        Set<String> claimedOnce = store.getClaimedOnce(p.getUniqueId());
        Map<String, Integer> counts = store.getPaidCounts(p.getUniqueId());

        if (r.isOneTime()) {
            if (claimedOnce.contains(r.id)) return State.CLAIMED;
            return (secs >= r.payFor) ? State.READY : State.LOCKED;
        }
        if (r.isRepeating()) {
            int done = counts.getOrDefault(r.id, 0);
            int should = (int) (secs / r.payEvery);
            if (should > done) return State.READY; // claimable (may autoclaim)
            return State.REPEATING; // between cycles
        }
        return State.LOCKED;
    }

    public boolean hasPermission(Player p, RewardEntry r) {
        // If the reward is rank-gated, require the exact node
        if (r.requiresPermission) {
            return p.hasPermission("oreo.prewards." + r.id);
        }
        // Otherwise: allow per-id or wildcard (do NOT grant to ops automatically)
        return p.hasPermission("oreo.prewards." + r.id) || p.hasPermission("oreo.prewards.*");
    }

    public List<String> rewardsReady(Player p) {
        return rewards.values().stream()
                .filter(r -> hasPermission(p, r))
                .filter(r -> stateOf(p, r) == State.READY)
                .map(r -> r.id)
                .collect(Collectors.toList());
    }

    // Prevent retroactive mass payouts by baselining repeating rewards once
    private void baselineRepeatingIfFirstSeen(Player p) {
        if (!baselineOnFirstSeen) return;
        UUID id = p.getUniqueId();
        Map<String, Integer> counts = store.getPaidCounts(id);
        if (counts.containsKey("__baseline__")) return;

        long secs = getPlaytimeSeconds(p);
        for (RewardEntry r : rewards.values()) {
            if (!hasPermission(p, r)) continue;
            if (r.isRepeating()) {
                int should = (int) (secs / r.payEvery);
                counts.put(r.id, should);
            }
        }
        counts.put("__baseline__", 1);
        store.setPaidCounts(id, counts);
        store.saveAsync();
    }

    public void checkPlayer(Player p, boolean autoClaim) {
        if (!enabled) return;

        baselineRepeatingIfFirstSeen(p); // handle first-seen baseline

        for (RewardEntry r : rewards.values()) {
            if (!hasPermission(p, r)) continue;
            State s = stateOf(p, r);
            if (s == State.READY && r.autoClaim && autoClaim) claim(p, r.id, false);
        }
    }

    public boolean claim(Player p, String id, boolean manual) {
        if (!enabled) {
            p.sendMessage(color("&cPlaytime Rewards are disabled."));
            return false;
        }
        RewardEntry r = rewards.get(id);
        if (r == null) return false;
        if (!hasPermission(p, r)) return false;

        long secs = getPlaytimeSeconds(p);
        Set<String> claimedOnce = store.getClaimedOnce(p.getUniqueId());
        Map<String, Integer> counts = store.getPaidCounts(p.getUniqueId());

        if (r.isOneTime()) {
            if (claimedOnce.contains(id)) return false;
            if (secs < r.payFor) return false;
            claimedOnce.add(id);
            store.setClaimedOnce(p.getUniqueId(), claimedOnce);
            executeCommands(p, r);
            store.saveAsync();
            return true;
        }

        if (r.isRepeating()) {
            int done = counts.getOrDefault(id, 0);
            int should = (int) (secs / r.payEvery);
            if (should <= done) return false;

            int toPay = should - done;
            if (!r.stackRewards && toPay > 1) toPay = 1;

            for (int i = 0; i < toPay; i++) {
                executeCommands(p, r);
            }
            counts.put(id, done + toPay);
            store.setPaidCounts(p.getUniqueId(), counts);
            store.saveAsync();
            return true;
        }

        return false;
    }

    private void executeCommands(Player p, RewardEntry r) {
        for (String cmd : r.commands) {
            runner.run(p, cmd.startsWith("/") ? cmd.substring(1) : cmd);
        }
        p.sendMessage(color("&aReward &f" + r.id + " &aclaimed!"));
    }

    public String color(String s) {
        return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', s);
    }
}
