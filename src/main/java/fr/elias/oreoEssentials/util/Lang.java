package fr.elias.oreoEssentials.util;

import fr.elias.oreoEssentials.OreoEssentials;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Map;

public final class Lang {
    private static OreoEssentials plugin;
    private static YamlConfiguration cfg;
    private static String prefix = "";

    private Lang() {}

    public static void init(OreoEssentials pl) {
        plugin = pl;
        File f = new File(plugin.getDataFolder(), "lang.yml");
        if (!f.exists()) plugin.saveResource("lang.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
        prefix = color(get("general.prefix", ""));
    }

    public static String get(String path, String def) {
        return cfg.getString(path, def);
    }
    public static java.util.List<String> getList(String path) {
        return cfg.getStringList(path);
    }

    public static String msg(String path, Map<String, String> vars, Player papiFor) {
        String raw = get(path, "");
        if (raw.isEmpty()) return "";

        // inject %prefix%
        raw = raw.replace("%prefix%", prefix);

        // simple variables (%kit_name%, etc.)
        if (vars != null) {
            for (var e : vars.entrySet()) {
                raw = raw.replace("%" + e.getKey() + "%", String.valueOf(e.getValue()));
            }
        }

        // PAPI (optional)
        if (papiFor != null && plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try { raw = PlaceholderAPI.setPlaceholders(papiFor, raw); } catch (Throwable ignored) {}
        }

        return color(raw);
    }

    public static void send(CommandSender to, String path, Map<String, String> vars, Player papiFor) {
        String s = msg(path, vars, papiFor);
        if (!s.isEmpty()) to.sendMessage(s);
    }

    public static String color(String s) { return s == null ? "" : s.replace('&','§').replace("\\n","\n"); }

    public static boolean getBool(String path, boolean def) { return cfg.getBoolean(path, def); }
    public static double getDouble(String path, double def) { return cfg.getDouble(path, def); }
    public static String timeHuman(long seconds) {
        // lang-driven humanizer
        boolean humanize = cfg.getBoolean("kits.time.humanize", true);
        if (!humanize) return String.valueOf(seconds) + "s";

        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60; seconds %= 60;
        long secs = seconds;

        String ud = get("kits.time.units.day", "d");
        String uh = get("kits.time.units.hour", "h");
        String um = get("kits.time.units.minute", "m");
        String us = get("kits.time.units.second", "s");
        int maxChunks = cfg.getInt("kits.time.max-chunks", 3);

        StringBuilder out = new StringBuilder();
        int chunks = 0;
        if (days > 0 && chunks < maxChunks) { out.append(days).append(ud).append(' '); chunks++; }
        if (hours > 0 && chunks < maxChunks) { out.append(hours).append(uh).append(' '); chunks++; }
        if (minutes > 0 && chunks < maxChunks) { out.append(minutes).append(um).append(' '); chunks++; }
        if (secs > 0 && chunks < maxChunks) { out.append(secs).append(us).append(' '); chunks++; }
        String s = out.toString().trim();
        return s.isEmpty() ? "0" + us : s;
    }
}
