package fr.elias.oreoEssentials.modgui.menu;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;

public class PerfToolsMenu implements InventoryProvider {

    private final OreoEssentials plugin;

    public PerfToolsMenu(OreoEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init(Player p, InventoryContents contents) {

        // Kill all hostile mobs
        contents.set(1, 3, ClickableItem.of(
                new ItemBuilder(Material.IRON_SWORD)
                        .name("&cKill all hostile mobs")
                        .lore("&7Removes all monsters in all worlds.")
                        .build(),
                e -> {
                    int removed = killHostileMobs();
                    p.sendMessage("§aRemoved §e" + removed + " §cmonsters§a from all worlds.");
                }
        ));

        // Kill all dropped items
        contents.set(1, 5, ClickableItem.of(
                new ItemBuilder(Material.HOPPER)
                        .name("&6Clear dropped items")
                        .lore("&7Removes all item entities in all worlds.")
                        .build(),
                e -> {
                    int removed = clearDroppedItems();
                    p.sendMessage("§aRemoved §e" + removed + " §7dropped items§a from all worlds.");
                }
        ));

        // Remove all primed TNT
        contents.set(2, 4, ClickableItem.of(
                new ItemBuilder(Material.TNT)
                        .name("&4Purge primed TNT")
                        .lore("&7Removes all primed TNT entities.")
                        .build(),
                e -> {
                    int removed = clearPrimedTnt();
                    p.sendMessage("§aRemoved §e" + removed + " §cprimed TNT§a from all worlds.");
                }
        ));
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        // No live update needed for now
    }

    /* ===================== Helpers ===================== */

    private int killHostileMobs() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Monster) {
                    entity.remove();
                    count++;
                }
            }
        }
        return count;
    }

    private int clearDroppedItems() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item) {
                    entity.remove();
                    count++;
                }
            }
        }
        return count;
    }

    private int clearPrimedTnt() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof TNTPrimed) {
                    entity.remove();
                    count++;
                }
            }
        }
        return count;
    }
}
