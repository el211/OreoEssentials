// File: src/main/java/fr/elias/oreoEssentials/kits/KitsManager.java
package fr.elias.oreoEssentials.kits;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import fr.elias.oreoEssentials.util.Lang;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
        this.menuTitle   = ItemParser.color(menuSec != null ? menuSec.getString("title", "&6Kits") : "&6Kits");
        this.menuRows    = Math.max(1, Math.min(6, menuSec != null ? menuSec.getInt("rows", 3) : 3));
        this.menuFill    = menuSec != null && menuSec.getBoolean("fill", true);
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

                List<String> commands = k.getStringList("commands");
                if (commands != null && commands.isEmpty()) commands = null;

                kits.put(id.toLowerCase(Locale.ROOT),
                        new Kit(id, name, icon, items, cd, slot, commands));
            }
        }
    }

    public Map<String, Kit> getKits() { return Collections.unmodifiableMap(kits); }

    public String getMenuTitle() { return menuTitle; }
    public int getMenuRows() { return menuRows; }
    public boolean isMenuFill() { return menuFill; }
    public String getFillMaterial() { return fillMaterial; }

    /** Returns seconds left of cooldown; 0 if ready. */
    public long getSecondsLeft(Player p, Kit kit) {
        if (p.hasPermission("oreo.kit.bypasscooldown")) return 0;
        if (kit.getCooldownSeconds() <= 0) return 0;

        long last = dataCfg.getLong("players." + p.getUniqueId() + "." + kit.getId(), 0);
        long now = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        long readyAt = last + kit.getCooldownSeconds();
        long left = readyAt - now;
        return Math.max(0, left);
    }

    public void markClaim(Player p, Kit kit) {
        long now = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        dataCfg.set("players." + p.getUniqueId() + "." + kit.getId(), now);
        saveData();
    }

    public void saveData() {
        try { dataCfg.save(dataFile); } catch (Exception ignored) {}
    }

    /** Tries to give the kit to player. Returns true if handled (success or message), false if kit unknown. */
    public boolean claim(Player p, String kitId) {
        Kit kit = kits.get(kitId.toLowerCase(Locale.ROOT));
        if (kit == null) {
            Lang.send(p, "kits.unknown-kit",
                    Map.of("kit_id", kitId),
                    p);
            return false;
        }

        if (!p.hasPermission("oreo.kit.claim")) {
            Lang.send(p, "kits.no-permission-claim", Map.of(), p);
            return true;
        }

        long left = getSecondsLeft(p, kit);
        if (left > 0 && !p.hasPermission("oreo.kit.bypasscooldown")) {
            Lang.send(p, "kits.cooldown",
                    Map.of(
                            "kit_name", kit.getDisplayName(),
                            "cooldown_left", Lang.timeHuman(left),
                            "cooldown_left_raw", String.valueOf(left)
                    ),
                    p);
            return true;
        } else if (left > 0) {
            Lang.send(p, "kits.bypass-cooldown",
                    Map.of("kit_name", kit.getDisplayName()),
                    p);
        }

        // Give items
        for (ItemStack it : kit.getItems()) {
            if (it == null) continue;
            var leftover = p.getInventory().addItem(it.clone());
            leftover.values().forEach(drop -> p.getWorld().dropItemNaturally(p.getLocation(), drop));
        }

        // Run commands
        if (kit.getCommands() != null) {
            for (String raw : kit.getCommands()) {
                runKitCommand(p, raw);
            }
            Lang.send(p, "kits.commands-ran",
                    Map.of("kit_name", kit.getDisplayName()), p);
        }

        markClaim(p, kit);
        Lang.send(p, "kits.claimed",
                Map.of("kit_name", kit.getDisplayName()), p);
        return true;
    }

    private void runKitCommand(Player p, String raw) {
        String line = raw == null ? "" : raw.trim();
        if (line.isEmpty()) return;

        String withPlayer = line.replace("%player%", p.getName());

        // Prefixes: console:, player:
        if (withPlayer.toLowerCase(Locale.ROOT).startsWith("console:")) {
            String cmd = withPlayer.substring("console:".length()).trim();
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            return;
        }
        if (withPlayer.toLowerCase(Locale.ROOT).startsWith("player:")) {
            String cmd = withPlayer.substring("player:".length()).trim();
            Bukkit.dispatchCommand(p, cmd);
            return;
        }

        // default: console
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), withPlayer);
    }
}
