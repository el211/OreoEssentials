package fr.elias.oreoEssentials.modgui.cfg;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class ModGuiConfig {
    private final OreoEssentials plugin;
    private File file;
    private FileConfiguration cfg;

    public ModGuiConfig(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    public void load() {
        try {
            file = new File(plugin.getDataFolder(), "modgui.yml");
            if (!file.exists()) {
                plugin.saveResource("modgui.yml", false);
            }
            cfg = YamlConfiguration.loadConfiguration(file);
        } catch (Throwable t) {
            plugin.getLogger().warning("[ModGUI] Failed to load modgui.yml: " + t.getMessage());
            cfg = new YamlConfiguration();
        }
        // ensure sections
        if (!cfg.isConfigurationSection("worlds")) cfg.createSection("worlds");
        saveSilently();
    }

    public void save() throws Exception { cfg.save(file); }
    private void saveSilently() { try { save(); } catch (Exception ignored) {} }

    private String wKey(World w) { return "worlds." + w.getName(); }

    // -------- Per-world whitelist --------
    public boolean worldWhitelistEnabled(World w) {
        return cfg.getBoolean(wKey(w) + ".whitelist.enabled", false);
    }
    public void setWorldWhitelistEnabled(World w, boolean v) {
        cfg.set(wKey(w) + ".whitelist.enabled", v);
        saveSilently();
    }
    public Set<UUID> worldWhitelist(World w) {
        List<String> list = cfg.getStringList(wKey(w) + ".whitelist.players");
        Set<UUID> out = new HashSet<>();
        for (String s : list) try { out.add(UUID.fromString(s)); } catch (Exception ignored) {}
        return out;
    }
    public void addWorldWhitelist(World w, UUID id) {
        Set<UUID> cur = worldWhitelist(w);
        cur.add(id);
        writeWorldWhitelist(w, cur);
    }
    public void removeWorldWhitelist(World w, UUID id) {
        Set<UUID> cur = worldWhitelist(w);
        cur.remove(id);
        writeWorldWhitelist(w, cur);
    }
    private void writeWorldWhitelist(World w, Set<UUID> set) {
        List<String> ls = set.stream().map(UUID::toString).toList();
        cfg.set(wKey(w) + ".whitelist.players", ls);
        saveSilently();
    }

    // -------- Per-world banned mobs --------
    public Set<String> bannedMobs(World w) {
        return new HashSet<>(cfg.getStringList(wKey(w) + ".banned-mobs"));
    }
    public void toggleMobBan(World w, String mobKey) {
        Set<String> s = bannedMobs(w);
        if (s.contains(mobKey)) s.remove(mobKey); else s.add(mobKey);
        cfg.set(wKey(w) + ".banned-mobs", new ArrayList<>(s));
        saveSilently();
    }
    public boolean isMobBanned(World w, String mobKey) {
        return bannedMobs(w).contains(mobKey);
    }

    // -------- Per-world gamerules (string→string raw; GUI controls common rules) --------
    public String gamerule(World w, String key, String def) {
        return cfg.getString(wKey(w) + ".gamerules." + key, def);
    }
    public void setGamerule(World w, String key, String value) {
        cfg.set(wKey(w) + ".gamerules." + key, value);
        saveSilently();
    }
}
