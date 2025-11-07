package fr.elias.oreoEssentials.modgui.menu;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.ModGuiService;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class MainMenu implements InventoryProvider {
    private final OreoEssentials plugin;
    private final ModGuiService svc;

    public MainMenu(OreoEssentials plugin, ModGuiService svc) {
        this.plugin = plugin;
        this.svc    = svc;
    }

    @Override
    public void init(Player p, InventoryContents c) {
        c.set(2, 2, ClickableItem.of(
                new ItemBuilder(Material.PLAYER_HEAD)
                        .name("&ePlayer moderation")
                        .lore("&7Ban, mute, kick, heal, feed, kill,", "&7invsee/ecsee, gm, freeze, vanish…")
                        .build(),
                e -> SmartInventory.builder()
                        .manager(plugin.getInvManager())              // REQUIRED
                        .id("modgui-player")
                        .provider(new PlayerMenu(plugin))
                        .title("§8Player moderation")
                        .size(6, 9)
                        .build()
                        .open(p)
        )) ;

        c.set(2, 4, ClickableItem.of(
                new ItemBuilder(Material.GRASS_BLOCK)
                        .name("&aWorld moderation")
                        .lore("&7Choose a world to manage time/weather/spawn,", "&7border, gamerules, per-world whitelist/mobs")
                        .build(),
                e -> SmartInventory.builder()
                        .manager(plugin.getInvManager())              // REQUIRED
                        .id("modgui-worlds")
                        .provider(new WorldListMenu(plugin, svc))
                        .title("§8Select World")
                        .size(6, 9)
                        .build()
                        .open(p)
        )) ;

        c.set(2, 6, ClickableItem.of(
                new ItemBuilder(Material.REDSTONE)
                        .name("&cServer moderation")
                        .lore("&7Difficulty, whitelist, default gm, quick spawns")
                        .build(),
                e -> SmartInventory.builder()
                        .manager(plugin.getInvManager())              // REQUIRED
                        .id("modgui-server")
                        .provider(new ServerMenu(plugin, svc))
                        .title("§8Server moderation")
                        .size(6, 9)
                        .build()
                        .open(p)
        )) ;
    }

    @Override
    public void update(Player player, InventoryContents contents) {}
}
