package fr.elias.oreoEssentials.modgui.menu;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.ModGuiService;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class WorldActionsMenu implements InventoryProvider {
    private final OreoEssentials plugin;
    private final ModGuiService svc;
    private final World world;

    public WorldActionsMenu(OreoEssentials plugin, ModGuiService svc, World world) {
        this.plugin = plugin; this.svc = svc; this.world = world;
    }

    @Override public void init(Player p, InventoryContents c) {
        // ===== Time =====
        c.set(1, 2, ClickableItem.of(
                new ItemBuilder(Material.CLOCK).name("&eTime: Day").lore("&7Now: " + world.getTime()).build(),
                e -> { setTime(1000); p.sendMessage("§eTime set to §6Day"); init(p, c); }
        ));
        c.set(1, 3, ClickableItem.of(
                new ItemBuilder(Material.CLOCK).name("&6Time: Sunset").lore("&7Now: " + world.getTime()).build(),
                e -> { setTime(12000); p.sendMessage("§eTime set to §6Sunset"); init(p, c); }
        ));
        c.set(1, 4, ClickableItem.of(
                new ItemBuilder(Material.CLOCK).name("&cTime: Night").lore("&7Now: " + world.getTime()).build(),
                e -> { setTime(18000); p.sendMessage("§eTime set to §6Night"); init(p, c); }
        ));

        // ===== Weather =====
        c.set(2, 2, ClickableItem.of(
                new ItemBuilder(Material.SUNFLOWER).name("&eWeather: Clear").lore(currentWeather()).build(),
                e -> { weather("sun"); p.sendMessage("§eWeather set to §6Clear"); init(p, c); }
        ));
        c.set(2, 3, ClickableItem.of(
                new ItemBuilder(Material.WATER_BUCKET).name("&bWeather: Rain").lore(currentWeather()).build(),
                e -> { weather("rain"); p.sendMessage("§eWeather set to §6Rain"); init(p, c); }
        ));
        c.set(2, 4, ClickableItem.of(
                new ItemBuilder(Material.TRIDENT).name("&3Weather: Storm").lore(currentWeather()).build(),
                e -> { weather("storm"); p.sendMessage("§eWeather set to §6Storm"); init(p, c); }
        ));

        // ===== Spawn =====
        c.set(3, 2, ClickableItem.of(
                new ItemBuilder(Material.RED_BED).name("&aSet World Spawn at your pos").build(),
                e -> {
                    world.setSpawnLocation(p.getLocation()); // Paper API supports Location
                    p.sendMessage("§aWorld spawn set at §f" +
                            p.getLocation().getBlockX() + " " +
                            p.getLocation().getBlockY() + " " +
                            p.getLocation().getBlockZ());
                }
        ));

        // ===== World border quick sizes =====
        c.set(3, 4, ClickableItem.of(
                new ItemBuilder(Material.IRON_BARS).name("&fBorder: 1k").lore(borderLore()).build(),
                e -> { border(1000); p.sendMessage("§eWorld border set to §61,000"); init(p, c); }
        ));
        c.set(3, 5, ClickableItem.of(
                new ItemBuilder(Material.IRON_BARS).name("&fBorder: 2k").lore(borderLore()).build(),
                e -> { border(2000); p.sendMessage("§eWorld border set to §62,000"); init(p, c); }
        ));
        c.set(3, 6, ClickableItem.of(
                new ItemBuilder(Material.IRON_BARS).name("&fBorder: 5k").lore(borderLore()).build(),
                e -> { border(5000); p.sendMessage("§eWorld border set to §65,000"); init(p, c); }
        ));

        // ===== Sub-menus (now with SmartInvs manager) =====
        c.set(4, 3, ClickableItem.of(
                new ItemBuilder(Material.BOOK).name("&bGamerules (per-world)").build(),
                e -> SmartInventory.builder()
                        .manager(plugin.getInvManager())
                        .provider(new WorldGamerulesMenu(plugin, svc, world))
                        .title("§8Gamerules: " + world.getName())
                        .size(6, 9)
                        .build()
                        .open(p)
        ));

        c.set(4, 5, ClickableItem.of(
                new ItemBuilder(Material.WHITE_WOOL).name("&fWhitelist (per-world)").build(),
                e -> SmartInventory.builder()
                        .manager(plugin.getInvManager())
                        .provider(new WorldWhitelistMenu(plugin, svc, world))
                        .title("§8Whitelist: " + world.getName())
                        .size(6, 9)
                        .build()
                        .open(p)
        ));

        c.set(4, 7, ClickableItem.of(
                new ItemBuilder(Material.ZOMBIE_HEAD).name("&2Banned mobs (per-world)").build(),
                e -> SmartInventory.builder()
                        .manager(plugin.getInvManager())
                        .provider(new WorldBannedMobsMenu(plugin, svc, world))
                        .title("§8Banned Mobs: " + world.getName())
                        .size(6, 9)
                        .build()
                        .open(p)
        ));
    }

    private String currentWeather() {
        boolean storm = world.hasStorm();
        boolean thun  = world.isThundering();
        String state = (!storm && !thun) ? "§eClear" : (storm && !thun) ? "§bRain" : "§3Storm";
        return "§7Current: " + state;
    }

    private String borderLore() {
        int size = (int) world.getWorldBorder().getSize();
        return "§7Current: §f" + size;
    }

    private void setTime(long ticks) {
        world.setTime(ticks);
    }

    private void weather(String mode) {
        switch (mode) {
            case "sun"   -> { world.setStorm(false); world.setThundering(false); }
            case "rain"  -> { world.setStorm(true);  world.setThundering(false); }
            case "storm" -> { world.setStorm(true);  world.setThundering(true);  }
        }
    }

    private void border(int size) {
        var wb = world.getWorldBorder();
        // Use existing center if set; otherwise default to current spawn
        if (wb.getCenter().getWorld() == null || !wb.getCenter().getWorld().equals(world)) {
            wb.setCenter(world.getSpawnLocation());
        }
        wb.setSize(size);
    }

    @Override public void update(Player player, InventoryContents contents) {}
}
