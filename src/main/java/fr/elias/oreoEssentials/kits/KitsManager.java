package fr.elias.oreoEssentials.kits;



import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;


import java.io.File;
import java.util.*;


public class KitsManager {
    private final OreoEssentials plugin;
    private File kitsFile;
    private FileConfiguration kitsCfg;
    private File dataFile;
    private FileConfiguration dataCfg;


    private boolean useItemsAdder;
    private String menuTitle;
    private int menuRows;
    private boolean menuFill;
    private String fillMaterial;


    private final Map<String, Kit> kits = new LinkedHashMap<>();


    public KitsManager(OreoEssentials plugin) {
        this.plugin = plugin;
        loadFiles();
        reload();
    }


    private void loadFiles() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        kitsFile = new File(plugin.getDataFolder(), "kits.yml");
        if (!kitsFile.exists()) plugin.saveResource("kits.yml", false);
        dataFile = new File(plugin.getDataFolder(), "kitsdata.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (Exception ignored) {}
        }
        kitsCfg = YamlConfiguration.loadConfiguration(kitsFile);
        dataCfg = YamlConfiguration.loadConfiguration(dataFile);
    }


    public void reload() {
        try { kitsCfg.load(kitsFile); } catch (Exception ignored) {}
        try { dataCfg.load(dataFile); } catch (Exception ignored) {}


        this.useItemsAdder = kitsCfg.getBoolean("use-itemadder", true) && ItemParser.isItemsAdderPresent();
        var menuSec = kitsCfg.getConfigurationSection("menu");
        this.menuTitle = ItemParser.color(menuSec != null ? menuSec.getString("title", "&6Kits") : "&6Kits");
        this.menuRows = Math.max(1, Math.min(6, menuSec != null ? menuSec.getInt("rows", 3) : 3));
        this.menuFill = menuSec != null && menuSec.getBoolean("fill", true);
        this.fillMaterial = menuSec != null ? menuSec.getString("fill-material", "GRAY_STAINED_GLASS_PANE") : "GRAY_STAINED_GLASS_PANE";


        kits.clear();
        ConfigurationSection sec = kitsCfg.getConfigurationSection("kits");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection k = sec.getConfigurationSection(id);
                if (k == null) continue;
                String name = ItemParser.color(k.getString("display-name", id));
                String iconDef = k.getString("icon", "STONE");
                ItemStack icon = ItemParser.parseItem(iconDef.startsWith("ia:") ? iconDef : "type:" + iconDef, useItemsAdder);
                long cd = k.getLong("cooldown-seconds", 0);
                Integer slot = k.contains("slot") ? k.getInt("slot") : null;


                List<ItemStack> items = new ArrayList<>();
                for (String line : k.getStringList("items")) {
                    ItemStack it = ItemParser.parseItem(line, useItemsAdder);
                    if (it != null) items.add(it);
                }
                kits.put(id.toLowerCase(Locale.ROOT), new Kit(id, name, icon, items, cd, slot));
            }
        }
    }


    public boolean claim(Player p, String kitId) {
        Kit kit = kits.get(kitId.toLowerCase(Locale.ROOT));
        if (kit == null) return false;
        if (!p.hasPermission("oreo.kit.claim")) return false;
        if (!p.hasPermission("oreo.kit.bypasscooldown")) {
            long left = getSecondsLeft(p, kit);
            if (left > 0) {
                p.sendMessage("§cYou must wait §e" + left + "s §cbefore claiming §6" + kit.getDisplayName() + "§c.");
                return true; // handled
            }
        }
    }