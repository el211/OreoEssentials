// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/HomesGuiProvider.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.services.HomeService;
import fr.elias.oreoEssentials.services.HomeService.StoredHome;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

import static org.bukkit.ChatColor.*;

public class HomesGuiProvider implements InventoryProvider {

    private final HomeService homes;

    // Layout: 6x9
    // header row (row 0): filler + center counter (slot 4) + refresh (slot 8)
    // content grid: rows 1..4, cols 1..7 (28 items/page). Footer prev/next at (5,0) / (5,8).
    public HomesGuiProvider(HomeService homes) {
        this.homes = homes;
    }

    @Override
    public void init(Player p, InventoryContents contents) {
        draw(p, contents);
    }

    @Override
    public void update(Player p, InventoryContents contents) {
        // manual refresh via button
    }

    private void draw(Player p, InventoryContents contents) {
        contents.fill(ClickableItem.empty(filler()));

        // Gather cross-server homes (name -> StoredHome)
        Map<String, StoredHome> data = safeListHomes(p.getUniqueId());
        List<String> names = new ArrayList<>(data.keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        int used = names.size();
        int max = guessMaxHomes(p);

        // Header: counter in top-middle (slot 0,4)
        contents.set(0, 4, ClickableItem.empty(counterItem(used, max)));

        // Header right: refresh button (slot 0,8)
        contents.set(0, 8, ClickableItem.of(refreshItem(), e -> {
            // Re-open same page
            contents.inventory().open(p, contents.pagination().getPage());
        }));

        // Pagination over content grid (rows 1..4, cols 1..7) -> 28 items per page
        Pagination pagination = contents.pagination();
        int pageSize = 28;

        ClickableItem[] items = names.stream().map(name -> {
            StoredHome sh = data.get(name);
            String server = (sh != null && sh.getServer() != null) ? sh.getServer() : homes.localServer();
            ItemStack it = homeItem(name, server, sh);
            return ClickableItem.of(it, e -> {
                switch (e.getClick()) {
                    case LEFT -> {
                        // Teleport (cross-server path)
                        HomesGuiCommand.crossServerTeleport(homes, p, name);
                    }
                    case RIGHT -> {
                        // Close current then open confirm
                        contents.inventory().close(p);
                        ConfirmDeleteGui.open(p, homes, name, () -> {
                            // onConfirm -> reopen current page refreshed
                            SmartInventory.builder()
                                    .id("oreo:homes")
                                    .provider(new HomesGuiProvider(homes))
                                    .size(6, 9)
                                    .title(DARK_GREEN + "Your Homes")
                                    .manager(OreoEssentials.get().getInvManager())
                                    .build()
                                    .open(p, pagination.getPage());
                        }, () -> {
                            // onCancel -> reopen
                            SmartInventory.builder()
                                    .id("oreo:homes")
                                    .provider(new HomesGuiProvider(homes))
                                    .size(6, 9)
                                    .title(DARK_GREEN + "Your Homes")
                                    .manager(OreoEssentials.get().getInvManager())
                                    .build()
                                    .open(p, pagination.getPage());
                        });
                    }
                    default -> {}
                }
            });
        }).toArray(ClickableItem[]::new);

        pagination.setItems(items);
        pagination.setItemsPerPage(pageSize);

        // Place items in grid (rows 1..4, cols 1..7)
        SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1);
        it.blacklist(1, 0); it.blacklist(1, 8);
        it.blacklist(2, 0); it.blacklist(2, 8);
        it.blacklist(3, 0); it.blacklist(3, 8);
        it.blacklist(4, 0); it.blacklist(4, 8);
        pagination.addToIterator(it);

        // Footer (row 5): Prev (5, 0) | Next (5, 8)
        if (!pagination.isFirst()) {
            contents.set(5, 0, ClickableItem.of(navItem(Material.ARROW, YELLOW + "Previous Page"), e ->
                    contents.inventory().open(p, pagination.previous().getPage())));
        }
        if (!pagination.isLast()) {
            contents.set(5, 8, ClickableItem.of(navItem(Material.ARROW, YELLOW + "Next Page"), e ->
                    contents.inventory().open(p, pagination.next().getPage())));
        }
    }

    /* ---------------- data helpers ---------------- */

    private Map<String, StoredHome> safeListHomes(UUID owner) {
        try {
            Map<String, StoredHome> m = homes.listHomes(owner);
            return (m == null) ? Collections.emptyMap() : m;
        } catch (Throwable t) {
            return Collections.emptyMap();
        }
    }

    private int guessMaxHomes(Player p) {
        try {
            var cfg = OreoEssentials.get().getConfigService();
            if (cfg != null) {
                try {
                    var m = cfg.getClass().getMethod("getMaxHomesFor", Player.class);
                    Object r = m.invoke(cfg, p);
                    if (r instanceof Number n) return n.intValue();
                } catch (NoSuchMethodException ignore) {}
                try {
                    var m2 = cfg.getClass().getMethod("defaultMaxHomes");
                    Object r2 = m2.invoke(cfg);
                    if (r2 instanceof Number n2) return n2.intValue();
                } catch (NoSuchMethodException ignore) {}
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    /* ---------------- item builders ---------------- */

    private ItemStack filler() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
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
            meta.setLore(List.of(GRAY + "Click to reload homes."));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack counterItem(int used, int max) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        String cap = (max > 0) ? used + "/" + max : used + "/?";
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Homes " + ChatColor.WHITE + "(" + cap + ")");
            meta.setLore(List.of(ChatColor.GRAY + "Left-click a home to teleport.",
                    ChatColor.GRAY + "Right-click a home to delete."));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack navItem(Material type, String name) {
        ItemStack it = new ItemStack(type);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack homeItem(String name, String server, StoredHome sh) {
        ItemStack it = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + name);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Server: " + ChatColor.YELLOW + server);
            if (sh != null) {
                lore.add(ChatColor.GRAY + "World: " + ChatColor.YELLOW + sh.getWorld());
                lore.add(ChatColor.GRAY + "XYZ: " + ChatColor.YELLOW + fmt(sh.getX()) + " "
                        + fmt(sh.getY()) + " " + fmt(sh.getZ()));
            }
            lore.add(" ");
            lore.add(ChatColor.GREEN + "Left-Click: " + ChatColor.WHITE + "Teleport");
            lore.add(ChatColor.RED + "Right-Click: " + ChatColor.WHITE + "Delete");
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
