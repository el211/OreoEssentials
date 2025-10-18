package fr.elias.oreoEssentials.customcraft;

import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.InventoryListener;
import fr.minuskube.inv.InventoryManager;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CraftDesignerMenu implements InventoryProvider {
    private final CustomCraftingService service;
    private final String recipeName;

    private final ItemStack[] grid = new ItemStack[9];
    private ItemStack result = null;
    private boolean shapeless = false;
    private String permission = null;

    private CraftDesignerMenu(Plugin plugin, CustomCraftingService service, String recipeName) {
        this.service = service;
        this.recipeName = recipeName;
        Arrays.fill(grid, null);
    }

    public static SmartInventory build(Plugin plugin, InventoryManager invMgr,
                                       CustomCraftingService service, String recipeName) {
        CraftDesignerMenu menu = new CraftDesignerMenu(plugin, service, recipeName);

        return SmartInventory.builder()
                .id("oecraft:" + recipeName)
                .provider(menu)
                .size(5, 9)
                .title(color(Lang.get("customcraft.gui.title", "&bOreoCraft — %name%").replace("%name%", recipeName)))
                .manager(invMgr)
                .closeable(true)
                .listener(new InventoryListener<>(InventoryCloseEvent.class, e -> {
                    if (e.getPlayer() instanceof Player p) menu.saveOnClose(p);
                }))
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        var filler = ui(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int r = 0; r < 5; r++) for (int c = 0; c < 9; c++) contents.set(r, c, ClickableItem.empty(filler));

        // Delete button
        drawDeleteButton(contents, player);

        // Labels
        contents.set(0, 2, ClickableItem.empty(ui(Material.BOOK,
                color(Lang.get("customcraft.gui.labels.ingredients", "&eIngredients (3×3)")))));
        contents.set(0, 6, ClickableItem.empty(ui(Material.EMERALD,
                color(Lang.get("customcraft.gui.labels.result", "&aResult →")))));

        // Load existing
        service.get(recipeName).ifPresent(r -> {
            ItemStack[] g = r.getGrid();
            for (int i = 0; i < 9; i++) grid[i] = (g[i] == null || g[i].getType().isAir()) ? null : g[i].clone();
            result     = (r.getResult() == null || r.getResult().getType().isAir()) ? null : r.getResult().clone();
            shapeless  = r.isShapeless();
            permission = r.getPermission();
        });

        drawModeToggle(contents);
        drawPermissionToggle(contents);

        // Grid 3×3
        for (int rr = 0; rr < 3; rr++) {
            for (int cc = 0; cc < 3; cc++) {
                int idx = rr * 3 + cc;
                SlotPos pos = SlotPos.of(1 + rr, 2 + cc);
                redrawGridCell(contents, pos, idx);
            }
        }

        // Result
        redrawResult(contents);
    }

    @Override public void update(Player player, InventoryContents contents) { /* no-op */ }

    /* ---------------- UI pieces ---------------- */

    private void drawDeleteButton(InventoryContents contents, Player player) {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(color(Lang.get("customcraft.gui.delete.name", "&cDelete this recipe")));

            List<String> lore = langList("customcraft.gui.delete.lore",
                    List.of("&7Click: &fInfo", "&7SHIFT+Click: &cCONFIRM deletion"));
            m.setLore(lore.stream().map(CraftDesignerMenu::color).toList());
            it.setItemMeta(m);
        }
        contents.set(0, 0, ClickableItem.of(it, (InventoryClickEvent e) -> {
            if (!e.isShiftClick()) {
                player.sendMessage(color(
                        Lang.get("customcraft.messages.delete-hint",
                                        "%prefix%&eTip: Hold &lSHIFT &eand click the barrier to delete &c%name%&e.")
                                .replace("%name%", recipeName)
                ));
                return;
            }
            boolean ok = service.delete(recipeName);
            if (ok) player.sendMessage(color(Lang.get("customcraft.messages.deleted", "%prefix%&aRecipe &e%name% &ahas been deleted.").replace("%name%", recipeName)));
            else    player.sendMessage(color(Lang.get("customcraft.messages.invalid", "%prefix%&cInvalid recipe. You need a result item and at least one ingredient.")));
            player.closeInventory();
        }));
    }

    private void drawModeToggle(InventoryContents contents) {
        ItemStack it = new ItemStack(shapeless ? Material.SLIME_BALL : Material.REDSTONE);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            String nameKey = shapeless ? "customcraft.gui.mode.shapeless-name" : "customcraft.gui.mode.shaped-name";
            String name = Lang.get(nameKey, shapeless ? "&aMode: SHAPELESS" : "&bMode: SHAPED");

            List<String> lore = new ArrayList<>();
            lore.add(Lang.get("customcraft.gui.mode.lore-common", "&7Click to toggle."));
            lore.add(Lang.get(shapeless ? "customcraft.gui.mode.lore-shapeless" : "customcraft.gui.mode.lore-shaped",
                    shapeless ? "&7Order doesn't matter." : "&7Layout matters."));

            m.setDisplayName(color(name));
            m.setLore(lore.stream().map(CraftDesignerMenu::color).toList());
            it.setItemMeta(m);
        }
        contents.set(0, 4, ClickableItem.of(it, (InventoryClickEvent e) -> {
            shapeless = !shapeless;
            drawModeToggle(contents);
        }));
    }

    /** Permission toggle at top-right (0,8). */
    private void drawPermissionToggle(InventoryContents contents) {
        boolean hasPerm = permission != null && !permission.isBlank();
        ItemStack it = new ItemStack(hasPerm ? Material.NAME_TAG : Material.PAPER);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            if (hasPerm) {
                m.setDisplayName(color(Lang.get("customcraft.gui.permission.required-name", "&6Permission required")));
                List<String> lore = langList("customcraft.gui.permission.required-lore",
                        List.of("&7Node: &e%permission%", "&7Click to make it &aPublic"));
                final String p = permission;
                m.setLore(lore.stream().map(s -> color(s.replace("%permission%", p))).toList());
            } else {
                m.setDisplayName(color(Lang.get("customcraft.gui.permission.public-name", "&aPublic")));
                List<String> lore = langList("customcraft.gui.permission.public-lore",
                        List.of("&7No permission required.", "&7Click to require a default node."));
                m.setLore(lore.stream().map(CraftDesignerMenu::color).toList());
            }
            it.setItemMeta(m);
        }
        contents.set(0, 8, ClickableItem.of(it, (InventoryClickEvent e) -> {
            boolean nowHas = permission != null && !permission.isBlank();
            if (nowHas) {
                permission = null; // make public
            } else {
                permission = "oreo.craft.use." + recipeName.toLowerCase(Locale.ROOT);
            }
            drawPermissionToggle(contents);
        }));
    }

    private void redrawGridCell(InventoryContents contents, SlotPos pos, int idx) {
        ItemStack display = (grid[idx] == null) ? new ItemStack(Material.AIR) : grid[idx].clone();
        contents.set(pos, ClickableItem.of(display, (InventoryClickEvent e) -> {
            ItemStack cursor = e.getWhoClicked().getItemOnCursor();
            ItemStack previous = grid[idx];

            grid[idx] = (cursor == null || cursor.getType().isAir()) ? null : cursor.clone();

            ItemStack newDisplay = (grid[idx] == null) ? new ItemStack(Material.AIR) : grid[idx].clone();
            contents.set(pos, ClickableItem.of(newDisplay, (InventoryClickEvent ev) -> {
                redrawGridCell(contents, pos, idx);
            }));

            e.getWhoClicked().setItemOnCursor(previous == null ? new ItemStack(Material.AIR) : previous.clone());
        }));
    }

    private void redrawResult(InventoryContents contents) {
        ItemStack display = (result == null) ? new ItemStack(Material.AIR) : result.clone();
        contents.set(2, 7, ClickableItem.of(display, (InventoryClickEvent e) -> {
            ItemStack cursor = e.getWhoClicked().getItemOnCursor();
            ItemStack old = result;

            result = (cursor == null || cursor.getType().isAir()) ? null : cursor.clone();

            ItemStack newDisplay = (result == null) ? new ItemStack(Material.AIR) : result.clone();
            contents.set(2, 7, ClickableItem.of(newDisplay, (InventoryClickEvent ev) -> {
                redrawResult(contents);
            }));

            e.getWhoClicked().setItemOnCursor(old == null ? new ItemStack(Material.AIR) : old.clone());
        }));
    }

    private ItemStack[] snapshotGrid() {
        ItemStack[] out = new ItemStack[9];
        for (int i = 0; i < 9; i++) out[i] = (grid[i] == null) ? null : grid[i].clone();
        return out;
    }

    private void saveOnClose(Player player) {
        CustomRecipe rec = new CustomRecipe(
                recipeName, snapshotGrid(),
                result == null ? null : result.clone(),
                shapeless, permission
        );
        boolean ok = service.saveAndRegister(rec);
        if (ok) {
            String mode = shapeless ? Lang.get("customcraft.format.mode.shapeless", "Shapeless")
                    : Lang.get("customcraft.format.mode.shaped", "Shaped");
            String permNote = (permission == null || permission.isBlank())
                    ? Lang.get("customcraft.format.permission.note-public", "&7(public)")
                    : Lang.get("customcraft.format.permission.note-required", "&6perm &e%permission%").replace("%permission%", permission);

            String msg = Lang.get("customcraft.messages.saved",
                    "%prefix%&aRecipe &e%name% &ahas been saved (&f%mode%&a) %perm_note%.");
            msg = msg.replace("%name%", recipeName)
                    .replace("%mode%", mode)
                    .replace("%perm_note%", permNote);
            player.sendMessage(color(msg));
        } else {
            player.sendMessage(color(Lang.get("customcraft.messages.invalid",
                    "%prefix%&cInvalid recipe. You need a result item and at least one ingredient.")));
        }
    }

    private static ItemStack ui(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); it.setItemMeta(meta); }
        return it;
    }

    /* ---------------- helpers ---------------- */

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    /** Read list from lang via basePath.0, basePath.1, …; return default if none. */
    private static List<String> langList(String basePath, List<String> def) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String v = Lang.get(basePath + "." + i, null);
            if (v == null) break;
            out.add(v);
        }
        if (out.isEmpty() && def != null) out.addAll(def);
        return out;
    }
}
