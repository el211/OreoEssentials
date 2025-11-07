package fr.elias.oreoEssentials.modgui.menu;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PlayerMenu implements InventoryProvider {
    private final OreoEssentials plugin;

    public PlayerMenu(OreoEssentials plugin) { this.plugin = plugin; }

    @Override
    public void init(Player p, InventoryContents c) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        int row = 1, col = 1;
        for (Player target : players) {
            var skull = new ItemBuilder(Material.PLAYER_HEAD)
                    .name("&b" + target.getName())
                    .lore("&7Click for actions")
                    .build();

            if (skull.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(target); // Player implements OfflinePlayer
                skull.setItemMeta(sm);
            }

            c.set(row, col, ClickableItem.of(skull, e ->
                    SmartInventory.builder()
                            .manager(plugin.getInvManager())               // ✅ REQUIRED
                            .id("modgui-player-" + target.getUniqueId())   // unique-ish per entry
                            .provider(new PlayerActionsMenu(plugin, target.getUniqueId()))
                            .title("§8Player: " + target.getName())
                            .size(6, 9)
                            .build()
                            .open(p)
            ));

            if (++col >= 8) { col = 1; row++; if (row >= 5) break; }
        }
    }

    @Override
    public void update(Player player, InventoryContents contents) {}
}
