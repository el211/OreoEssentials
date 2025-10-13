package fr.elias.oreoEssentials.playervaults.gui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.playervaults.PlayerVaultsConfig;
import fr.elias.oreoEssentials.playervaults.PlayerVaultsService;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Menu listing all vault IDs; locked ones show a barrier with custom text. */
public final class VaultMenu {

    public static SmartInventory build(OreoEssentials plugin,
                                       PlayerVaultsService svc,
                                       PlayerVaultsConfig cfg,
                                       List<Integer> vaultIds) {
        int rows = Math.min(Math.max(1, (int) Math.ceil(vaultIds.size() / 9.0)), 6);
        return SmartInventory.builder()
                .manager(plugin.getInvManager()) // REQUIRED for embedded SmartInvs
                .id("oe_vault_menu")
                .provider(new Provider(svc, cfg, vaultIds))
                .size(rows, 9)
                .title(ChatColor.translateAlternateColorCodes('&', cfg.menuTitle()))
                .build();
    }

    private static final class Provider implements InventoryProvider {
        private final PlayerVaultsService svc;
        private final PlayerVaultsConfig cfg;
        private final List<Integer> ids;

        Provider(PlayerVaultsService svc, PlayerVaultsConfig cfg, List<Integer> ids) {
            this.svc = svc; this.cfg = cfg; this.ids = ids;
        }

        @Override
        public void init(Player player, InventoryContents contents) {
            for (int i = 0; i < ids.size(); i++) {
                int id = ids.get(i);
                boolean unlocked = svc.canAccess(player, id); // <-- was hasUsePermission

                ItemStack icon = new ItemStack(unlocked ? Material.CHEST : Material.BARRIER);
                ItemMeta meta = icon.getItemMeta();

                String name = (unlocked ? cfg.menuItemUnlockedName() : cfg.menuItemLockedName())
                        .replace("<id>", String.valueOf(id));
                String lore = (unlocked ? cfg.menuItemUnlockedLore() : cfg.menuItemLockedLore())
                        .replace("<id>", String.valueOf(id));

                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
                meta.setLore(List.of(ChatColor.translateAlternateColorCodes('&', lore)));
                icon.setItemMeta(meta);

                contents.set(i / 9, i % 9, ClickableItem.of(icon, e -> {
                    if (!svc.hasUsePermission(player, id)) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                cfg.denyMessage().replace("%id%", String.valueOf(id))));
                        player.playSound(player.getLocation(), cfg.denySound(), 1f, 0.7f);
                        return;
                    }
                    svc.openVault(player, id);
                }));
            }
        }

        @Override public void update(Player player, InventoryContents contents) {}
    }
}
