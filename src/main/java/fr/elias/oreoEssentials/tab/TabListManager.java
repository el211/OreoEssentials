package fr.elias.oreoEssentials.tab;



import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;


import java.io.File;
import java.lang.reflect.Method;


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


    public TabListManager(OreoEssentials plugin) {
        this.plugin = plugin;
        load();
        if (enabled) start();
    }


    public void load() {
        file = new File(plugin.getDataFolder(), "tab.yml");
        if (!file.exists()) plugin.saveResource("tab.yml", false);
        cfg = YamlConfiguration.loadConfiguration(file);
        enabled = cfg.getBoolean("tab.enabled", true);
        usePapi = cfg.getBoolean("tab.use-placeholderapi", true) && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        header = color(cfg.getString("tab.header", ""));
        footer = color(cfg.getString("tab.footer", ""));
        intervalTicks = Math.max(20, cfg.getInt("tab.interval-ticks", 200));
    }


    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                String h = applyPapi(p, header);
                String f = applyPapi(p, footer);
                setTab(p, h, f);
            }
        }, 1L, intervalTicks);
    }


    public void stop() { if (task != null) { task.cancel(); task = null; } }


    private String applyPapi(Player p, String s) {
        if (!usePapi || s == null || s.isEmpty()) return s;
        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method m = papi.getMethod("setPlaceholders", Player.class, String.class);
            return (String) m.invoke(null, p, s);
        } catch (Throwable ignored) { return s; }
    }


    private void setTab(Player player, String header, String footer) {
        player.setPlayerListHeaderFooter(header, footer);
    }


    private String color(String s) { return s == null ? "" : s.replace('&', '§').replace("\\n", "\n"); }
}