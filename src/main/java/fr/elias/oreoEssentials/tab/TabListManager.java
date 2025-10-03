package fr.elias.oreoEssentials.tab;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

public class TabListManager {
    private final OreoEssentials plugin;
    private File file;
    private FileConfiguration cfg;
    private BukkitTask task;

    private boolean enabled;
    private boolean usePapi;
    private String header;
    private String footer;
    private int intervalTicks;

    // Title
    private boolean titleEnabled;
    private boolean titleShowOnJoin;
    private String titleText;
    private String titleSub;
    private int titleIn, titleStay, titleOut;

    // Name format
    private boolean nameEnabled;
    private boolean useRankFormats;
    private String rankKey; // PAPI placeholder used to select a rank format
    private String namePattern; // fallback pattern when no rank formats / no default
    private boolean nameEnforceMax;
    private int nameMaxLen;
    private OverflowMode overflowMode;
    private Map<String, String> rankFormats = new HashMap<>(); // lowercased rank -> pattern

    // Per world overrides
    private Map<String, WorldOverrides> worldOverrides = new HashMap<>();

    // track who already received title on this session
    private final Set<UUID> titleShown = new HashSet<>();

    private enum OverflowMode { TRIM, ELLIPSIS }

    private static class WorldOverrides {
        Boolean usePapi;
        String header;
        String footer;
        NameOverrides name;
    }

    private static class NameOverrides {
        Boolean enabled;
        Boolean useRankFormats;
        String rankKey;
        Map<String, String> rankFormats;
        String pattern;
        Boolean enforceMax;
        Integer maxLen;
        String overflow;
    }

    public TabListManager(OreoEssentials plugin) {
        this.plugin = plugin;
        load();
        if (enabled) start();
        if (titleEnabled && titleShowOnJoin) {
            Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
                @org.bukkit.event.EventHandler
                public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
                    sendTitleOnce(e.getPlayer());
                }
            }, plugin);
        }
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "tab.yml");
        if (!file.exists()) plugin.saveResource("tab.yml", false);
        cfg = YamlConfiguration.loadConfiguration(file);

        enabled = cfg.getBoolean("tab.enabled", true);
        usePapi = cfg.getBoolean("tab.use-placeholderapi", true)
                && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

        header = color(cfg.getString("tab.header", ""));
        footer = color(cfg.getString("tab.footer", ""));
        intervalTicks = Math.max(20, cfg.getInt("tab.interval-ticks", 200));

        // title
        ConfigurationSection t = cfg.getConfigurationSection("tab.title");
        titleEnabled = t != null && t.getBoolean("enabled", false);
        titleShowOnJoin = t != null && t.getBoolean("show-on-join", true);
        titleText = color(t != null ? t.getString("text", "") : "");
        titleSub = color(t != null ? t.getString("subtitle", "") : "");
        titleIn = t != null ? t.getInt("fade-in", 10) : 10;
        titleStay = t != null ? t.getInt("stay", 60) : 60;
        titleOut = t != null ? t.getInt("fade-out", 10) : 10;

        // name-format
        ConfigurationSection nf = cfg.getConfigurationSection("tab.name-format");
        nameEnabled = nf != null && nf.getBoolean("enabled", true);
        useRankFormats = nf != null && nf.getBoolean("use-rank-formats", true);
        rankKey = nf != null ? nf.getString("rank-key", "%luckperms_primary_group%") : "%luckperms_primary_group%";
        namePattern = nf != null ? nf.getString("pattern", "&7%nick_or_name%") : "&7%nick_or_name%";
        nameEnforceMax = nf != null && nf.getBoolean("enforce-max-length", true);
        nameMaxLen = nf != null ? Math.max(1, nf.getInt("max-length", 16)) : 16;
        String ov = nf != null ? nf.getString("overflow", "TRIM") : "TRIM";
        overflowMode = "ELLIPSIS".equalsIgnoreCase(ov) ? OverflowMode.ELLIPSIS : OverflowMode.TRIM;

        rankFormats.clear();
        if (nf != null && nf.isConfigurationSection("rank-formats")) {
            for (String k : nf.getConfigurationSection("rank-formats").getKeys(false)) {
                rankFormats.put(k.toLowerCase(Locale.ROOT), nf.getString("rank-formats." + k));
            }
        }

        // per-world overrides
        worldOverrides.clear();
        ConfigurationSection pw = cfg.getConfigurationSection("tab.per-world");
        if (pw != null) {
            for (String world : pw.getKeys(false)) {
                ConfigurationSection w = pw.getConfigurationSection(world);
                if (w == null) continue;
                WorldOverrides o = new WorldOverrides();
                o.usePapi = w.isSet("use-placeholderapi") ? w.getBoolean("use-placeholderapi") : null;
                o.header = w.isSet("header") ? color(w.getString("header")) : null;
                o.footer = w.isSet("footer") ? color(w.getString("footer")) : null;

                if (w.isConfigurationSection("name-format")) {
                    ConfigurationSection wn = w.getConfigurationSection("name-format");
                    NameOverrides no = new NameOverrides();
                    no.enabled = wn.isSet("enabled") ? wn.getBoolean("enabled") : null;
                    no.useRankFormats = wn.isSet("use-rank-formats") ? wn.getBoolean("use-rank-formats") : null;
                    no.rankKey = wn.isSet("rank-key") ? wn.getString("rank-key") : null;
                    if (wn.isConfigurationSection("rank-formats")) {
                        Map<String, String> map = new HashMap<>();
                        for (String k : wn.getConfigurationSection("rank-formats").getKeys(false)) {
                            map.put(k.toLowerCase(Locale.ROOT), wn.getString("rank-formats." + k));
                        }
                        no.rankFormats = map;
                    }
                    no.pattern = wn.isSet("pattern") ? wn.getString("pattern") : null;
                    no.enforceMax = wn.isSet("enforce-max-length") ? wn.getBoolean("enforce-max-length") : null;
                    no.maxLen = wn.isSet("max-length") ? wn.getInt("max-length") : null;
                    no.overflow = wn.isSet("overflow") ? wn.getString("overflow") : null;
                    o.name = no;
                }
                worldOverrides.put(world.toLowerCase(Locale.ROOT), o);
            }
        }
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                // per-world overrides
                WorldOverrides o = worldOverrides.get(p.getWorld().getName().toLowerCase(Locale.ROOT));
                boolean papi = (o != null && o.usePapi != null) ? (o.usePapi && hasPapi()) : usePapi;

                // header/footer
                String h = firstNonNull(o != null ? o.header : null, header);
                String f = firstNonNull(o != null ? o.footer : null, footer);
                h = runPapiIf(papi, p, h);
                f = runPapiIf(papi, p, f);
                setTab(p, h, f);

                // name format
                boolean nEnabled = (o != null && o.name != null && o.name.enabled != null)
                        ? o.name.enabled : nameEnabled;
                if (!nEnabled) continue;

                // resolve rank key (via PAPI)
                boolean localUseRank = (o != null && o.name != null && o.name.useRankFormats != null)
                        ? o.name.useRankFormats : useRankFormats;

                String localRankKey = (o != null && o.name != null && o.name.rankKey != null)
                        ? o.name.rankKey : rankKey;

                String localPattern = (o != null && o.name != null && o.name.pattern != null)
                        ? o.name.pattern : namePattern;

                Map<String, String> localRankFormats = (o != null && o.name != null && o.name.rankFormats != null)
                        ? o.name.rankFormats : rankFormats;

                // choose pattern
                String patternToUse = localPattern;
                if (localUseRank && !localRankFormats.isEmpty()) {
                    String rk = runPapiIf(papi, p, localRankKey);
                    if (rk == null) rk = "";
                    String byRank = localRankFormats.get(rk.toLowerCase(Locale.ROOT));
                    if (byRank == null) byRank = localRankFormats.get("default");
                    if (byRank != null) patternToUse = byRank;
                }

                // build name
                String nickOrName = safeNickOrName(p);
                String rendered = patternToUse.replace("%nick_or_name%", nickOrName);
                rendered = runPapiIf(papi, p, rendered);

                // enforce max length
                boolean enforce = (o != null && o.name != null && o.name.enforceMax != null)
                        ? o.name.enforceMax : nameEnforceMax;
                int maxLen = (o != null && o.name != null && o.name.maxLen != null)
                        ? o.name.maxLen : nameMaxLen;
                String ov = (o != null && o.name != null && o.name.overflow != null)
                        ? o.name.overflow : (overflowMode == OverflowMode.ELLIPSIS ? "ELLIPSIS" : "TRIM");
                OverflowMode mode = "ELLIPSIS".equalsIgnoreCase(ov) ? OverflowMode.ELLIPSIS : OverflowMode.TRIM;

                if (enforce) rendered = fitToMax(rendered, maxLen, mode);

                try { p.setPlayerListName(rendered); } catch (Throwable ignored) {}
            }
        }, 1L, intervalTicks);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        titleShown.clear();
    }

    private void sendTitleOnce(Player p) {
        if (!titleEnabled || !titleShowOnJoin) return;
        if (titleShown.contains(p.getUniqueId())) return;
        String t = runPapiIf(usePapi && hasPapi(), p, titleText);
        String s = runPapiIf(usePapi && hasPapi(), p, titleSub);
        try { p.sendTitle(t, s, titleIn, titleStay, titleOut); } catch (Throwable ignored) {}
        titleShown.add(p.getUniqueId());
    }

    private boolean hasPapi() {
        return Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    private String runPapiIf(boolean papi, Player p, String s) {
        if (!papi || s == null || s.isEmpty()) return s;
        try {
            Class<?> papiCls = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method m = papiCls.getMethod("setPlaceholders", Player.class, String.class);
            return (String) m.invoke(null, p, s);
        } catch (Throwable ignored) { return s; }
    }

    private void setTab(Player player, String header, String footer) {
        try { player.setPlayerListHeaderFooter(header, footer); } catch (Throwable ignored) {}
    }

    private static String firstNonNull(String a, String b) { return a != null ? a : b; }

    private static String color(String s) { return s == null ? "" : s.replace('&', '§').replace("\\n", "\n"); }

    /** Prefer displayName (/nick) when available. */
    private static String safeNickOrName(Player p) {
        try {
            String d = p.getDisplayName();
            if (d != null && !d.isEmpty()) return d;
        } catch (Throwable ignored) {}
        return p.getName();
    }

    /** Enforce vanilla 16-char player list limit safely (without breaking color codes). */
    private static String fitToMax(String s, int max, OverflowMode mode) {
        if (s == null) return "";
        int visible = 0;
        StringBuilder out = new StringBuilder();
        char[] arr = s.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            char c = arr[i];
            // keep color codes intact (normalize to §)
            if ((c == '§' || c == '&') && i + 1 < arr.length) {
                out.append('§');
                out.append(arr[++i]);
                continue;
            }
            if (visible < max) {
                out.append(c);
                visible++;
            } else {
                if (mode == OverflowMode.ELLIPSIS) {
                    return trimWithEllipsis(out.toString(), max);
                }
                break;
            }
        }
        return out.toString();
    }

    private static String trimWithEllipsis(String colored, int max) {
        // simplest: strip colors to compute then just return plain trimmed + "..."
        String raw = colored.replaceAll("(?i)§[0-9A-FK-ORX]", "");
        if (max <= 3) return "...".substring(0, Math.min(3, max));
        if (raw.length() <= max) return raw;
        return raw.substring(0, max - 3) + "...";
    }
}
