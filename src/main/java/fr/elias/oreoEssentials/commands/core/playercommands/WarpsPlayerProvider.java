package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.services.WarpDirectory;
import fr.elias.oreoEssentials.services.WarpService;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

import static org.bukkit.ChatColor.*;

public class WarpsPlayerProvider implements InventoryProvider {

    private final WarpService warps;

    public WarpsPlayerProvider(WarpService warps) { this.warps = warps; }

    @Override
    public void init(Player p, InventoryContents contents) { draw(p, contents); }

    @Override
    public void update(Player p, InventoryContents contents) {}

    private void draw(Player p, InventoryContents contents) {
        contents.fill(ClickableItem.empty(filler()));

        final OreoEssentials plugin = OreoEssentials.get();
        final String localServer = plugin.getConfigService().serverName();
        final WarpDirectory dir = plugin.getWarpDirectory();

        List<String> names = new ArrayList<>(safeListWarps());
        names.sort(String.CASE_INSENSITIVE_ORDER);

        // Header + refresh
        contents.set(0, 4, ClickableItem.empty(counterItem(names.size())));
        contents.set(0, 8, ClickableItem.of(refreshItem(), e ->
                contents.inventory().open(p, contents.pagination().getPage())));

        ClickableItem[] items = names.stream().map(displayName -> {
            final String key = displayName.toLowerCase(Locale.ROOT);
            String server = (dir != null ? dir.getWarpServer(key) : localServer);
            if (server == null || server.isBlank()) server = localServer;

            Location loc = null;
            if (server.equalsIgnoreCase(localServer)) {
                try { loc = warps.getWarp(key); } catch (Throwable ignored) {}
            }

            final boolean allowed = warps.canUse(p, key);
            ItemStack icon = warpPlayerItem(displayName, server, loc, allowed);

            return ClickableItem.of(icon, e -> {
                if (!allowed) {
                    p.sendMessage(ChatColor.RED + "You don't have permission for this warp.");
                    return;
                }
                // Uses the admin teleport helper to keep behavior consistent (local/cross-server)
                WarpsAdminCommand.crossServerTeleport(warps, p, displayName);
            });
        }).toArray(ClickableItem[]::new);

        Pagination pagination = contents.pagination();
        pagination.setItems(items);
        pagination.setItemsPerPage(28);

        SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1);
        it.blacklist(1, 0); it.blacklist(1, 8);
        it.blacklist(2, 0); it.blacklist(2, 8);
        it.blacklist(3, 0); it.blacklist(3, 8);
        it.blacklist(4, 0); it.blacklist(4, 8);
        pagination.addToIterator(it);

        if (!pagination.isFirst()) {
            contents.set(5, 0, ClickableItem.of(nav(Material.ARROW, YELLOW + "Previous Page"),
                    e -> contents.inventory().open(p, pagination.previous().getPage())));
        }
        if (!pagination.isLast()) {
            contents.set(5, 8, ClickableItem.of(nav(Material.ARROW, YELLOW + "Next Page"),
                    e -> contents.inventory().open(p, pagination.next().getPage())));
        }
    }

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
        if (meta != null) { meta.setDisplayName(" "); it.setItemMeta(meta); }
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
            meta.setLore(List.of(GRAY + "Left-click: " + WHITE + "Teleport"));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack nav(Material type, String name) {
        ItemStack it = new ItemStack(type);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); it.setItemMeta(meta); }
        return it;
    }

    private ItemStack warpPlayerItem(String name, String server, Location loc, boolean allowed) {
        ItemStack it = new ItemStack(allowed ? Material.LODESTONE : Material.BARRIER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((allowed ? ChatColor.AQUA : ChatColor.DARK_RED) + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Server: " + ChatColor.YELLOW + server);
            if (loc != null) {
                lore.add(ChatColor.GRAY + "World: " + ChatColor.YELLOW + loc.getWorld().getName());
                lore.add(ChatColor.GRAY + "XYZ: " + ChatColor.YELLOW +
                        fmt(loc.getX()) + " " + fmt(loc.getY()) + " " + fmt(loc.getZ()));
            }
            lore.add(" ");
            lore.add(allowed
                    ? ChatColor.GREEN + "Left-Click: " + ChatColor.WHITE + "Teleport"
                    : ChatColor.RED + "You don't have access.");
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
