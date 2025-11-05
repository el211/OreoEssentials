// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/WarpsAdminProvider.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.services.WarpDirectory;
import fr.elias.oreoEssentials.services.WarpService;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

import static org.bukkit.ChatColor.*;

public class WarpsAdminProvider implements InventoryProvider {

    private final WarpService warps;

    public WarpsAdminProvider(WarpService warps) {
        this.warps = warps;
    }

    @Override
    public void init(Player p, InventoryContents contents) {
        draw(p, contents);
    }

    @Override
    public void update(Player p, InventoryContents contents) {
        // static; manual refresh via button
    }

    private void draw(Player p, InventoryContents contents) {
        contents.fill(ClickableItem.empty(filler()));

        final OreoEssentials plugin = OreoEssentials.get();
        final String localServer = plugin.getConfigService().serverName();
        final WarpDirectory dir = plugin.getWarpDirectory();

        // Collect and sort warps
        List<String> names = new ArrayList<>(safeListWarps());
        names.sort(String.CASE_INSENSITIVE_ORDER);

        // Header counter + refresh
        contents.set(0, 4, ClickableItem.empty(counterItem(names.size())));
        contents.set(0, 8, ClickableItem.of(refreshItem(), e ->
                contents.inventory().open(p, contents.pagination().getPage())));

        // Build grid items
        ClickableItem[] items = names.stream().map(displayName -> {
            final String key = displayName.toLowerCase(Locale.ROOT);

            String server = (dir != null ? dir.getWarpServer(key) : localServer);
            if (server == null || server.isBlank()) server = localServer;

            Location loc = null;
            if (server.equalsIgnoreCase(localServer)) {
                try { loc = warps.getWarp(key); } catch (Throwable ignored) {}
            }

            final String currentPerm = (dir == null ? null : dir.getWarpPermission(key));
            final boolean protectedMode = currentPerm != null && !currentPerm.isBlank();

            ItemStack icon = warpAdminItem(displayName, server, loc, protectedMode, currentPerm);

            return ClickableItem.of(icon, e -> {
                ClickType type = e.getClick();

                // Quick-teleport convenience
                if (type == ClickType.SHIFT_LEFT || type == ClickType.MIDDLE) {
                    WarpsAdminCommand.crossServerTeleport(warps, p, displayName);
                    return;
                }

                // Open actions GUI for all other clicks (LEFT/RIGHT/etc.)
                SmartInventory.builder()
                        .id("oreo:warps_admin_actions:" + key)
                        .provider(new WarpAdminActionsProvider(warps, key))
                        .size(4, 9)
                        .title(ChatColor.DARK_AQUA + "Warp: " + ChatColor.AQUA + displayName)
                        .manager(OreoEssentials.get().getInvManager())
                        .build()
                        .open(p);
            });
        }).toArray(ClickableItem[]::new);

        // Pagination: rows 1..4, cols 1..7 (28 per page)
        Pagination pagination = contents.pagination();
        pagination.setItems(items);
        pagination.setItemsPerPage(28);

        SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1);
        it.blacklist(1, 0); it.blacklist(1, 8);
        it.blacklist(2, 0); it.blacklist(2, 8);
        it.blacklist(3, 0); it.blacklist(3, 8);
        it.blacklist(4, 0); it.blacklist(4, 8);
        pagination.addToIterator(it);

        // Footer nav
        if (!pagination.isFirst()) {
            contents.set(5, 0, ClickableItem.of(nav(Material.ARROW, YELLOW + "Previous Page"),
                    e -> contents.inventory().open(p, pagination.previous().getPage())));
        }
        if (!pagination.isLast()) {
            contents.set(5, 8, ClickableItem.of(nav(Material.ARROW, YELLOW + "Next Page"),
                    e -> contents.inventory().open(p, pagination.next().getPage())));
        }
    }

    /* ---------------- data + items ---------------- */

    private Set<String> safeListWarps() {
        try {
            Set<String> s = warps.listWarps();
            return (s == null) ? Collections.emptySet() : s;
        } catch (Throwable t) {
            return Collections.emptySet();
        }
    }

    private ItemStack filler() {
        ItemStack it = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack refreshItem() {
        ItemStack it = new ItemStack(Material.SUNFLOWER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(YELLOW + "Refresh");
            meta.setLore(List.of(GRAY + "Click to reload warps."));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack counterItem(int count) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Warps " + ChatColor.WHITE + "(" + count + ")");
            List<String> lore = new ArrayList<>();
            lore.add(GRAY + "Left/Right-click: " + WHITE + "Manage warp");
            lore.add(GRAY + "Shift-Left or Middle-click: " + WHITE + "Quick Teleport");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack nav(Material type, String name) {
        ItemStack it = new ItemStack(type);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack warpAdminItem(String name, String server, Location loc, boolean protectedMode, String perm) {
        ItemStack it = new ItemStack(Material.LODESTONE);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Server: " + ChatColor.YELLOW + server);
            if (loc != null && loc.getWorld() != null) {
                lore.add(ChatColor.GRAY + "World: " + ChatColor.YELLOW + loc.getWorld().getName());
                lore.add(ChatColor.GRAY + "XYZ: " + ChatColor.YELLOW +
                        fmt(loc.getX()) + " " + fmt(loc.getY()) + " " + fmt(loc.getZ()));
            }
            lore.add(" ");
            lore.add(ChatColor.GRAY + "Permission: " + (protectedMode
                    ? (ChatColor.GOLD + (perm == null || perm.isBlank() ? "(custom)" : perm))
                    : (ChatColor.GREEN + "public")));
            lore.add(" ");
            lore.add(ChatColor.GREEN + "Click: " + ChatColor.WHITE + "Open actions");
            lore.add(ChatColor.AQUA + "Shift-Left/Middle: " + ChatColor.WHITE + "Quick teleport");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private String fmt(double d) {
        return String.valueOf(Math.round(d * 10.0) / 10.0);
    }
}
