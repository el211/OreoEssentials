package fr.elias.oreoEssentials.customcraft;

import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.InventoryListener;
import fr.minuskube.inv.InventoryManager;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CraftDesignerMenu implements InventoryProvider {
    private final CustomCraftingService service;
    private final String recipeName;

    // State
    private final ItemStack[] grid = new ItemStack[9]; // 3×3
    private ItemStack result = null;
    private boolean shapeless = false;
    private String permission = null; // null/blank => public

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
                .title("§bOreoCraft — " + recipeName)
                .manager(invMgr)
                .closeable(true)
                .listener(new InventoryListener<>(InventoryCloseEvent.class, e -> {
                    if (e.getPlayer() instanceof Player p) menu.saveOnClose(p);
                }))
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        // Frame
        var filler = ui(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int r = 0; r < 5; r++) for (int c = 0; c < 9; c++) contents.set(r, c, ClickableItem.empty(filler));

        // Delete button (top-left)
        drawDeleteButton(contents, player);

        // Labels
        contents.set(0, 2, ClickableItem.empty(ui(Material.BOOK, "§eIngrédients (3×3)")));
        contents.set(0, 6, ClickableItem.empty(ui(Material.EMERALD, "§aRésultat →")));

        // Load existing (must happen before toggles so they reflect state)
        service.get(recipeName).ifPresent(r -> {
            ItemStack[] g = r.getGrid();
            for (int i = 0; i < 9; i++) grid[i] = (g[i] == null || g[i].getType().isAir()) ? null : g[i].clone();
            result     = (r.getResult() == null || r.getResult().getType().isAir()) ? null : r.getResult().clone();
            shapeless  = r.isShapeless();
            permission = r.getPermission(); // may be null
        });

        // Toggles (top center + top right)
        drawModeToggle(contents);
        drawPermissionToggle(contents);

        // Grid 3×3 (1..3, 2..4)
        for (int rr = 0; rr < 3; rr++) {
            for (int cc = 0; cc < 3; cc++) {
                int idx = rr * 3 + cc;
                SlotPos pos = SlotPos.of(1 + rr, 2 + cc);
                redrawGridCell(contents, pos, idx);
            }
        }

        // Result (2,7)
        redrawResult(contents);
    }

    @Override public void update(Player player, InventoryContents contents) { /* no-op */ }

    /* ---------------- UI pieces ---------------- */

    private void drawDeleteButton(InventoryContents contents, Player player) {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName("§cSupprimer cette recette");
            m.setLore(List.of(
                    "§7Clique: §finfo",
                    "§7MAJ+Clique: §cCONFIRMER la suppression"
            ));
            it.setItemMeta(m);
        }
        contents.set(0, 0, ClickableItem.of(it, (InventoryClickEvent e) -> {
            if (!e.isShiftClick()) {
                player.sendMessage("§eAstuce: Maintiens §e§lSHIFT§e et clique pour supprimer §c" + recipeName + "§e.");
                return;
            }
            boolean ok = service.delete(recipeName);
            if (ok) player.sendMessage("§a[OreoCraft] Recette §e" + recipeName + "§a supprimée.");
            else    player.sendMessage("§c[OreoCraft] Échec de la suppression de §e" + recipeName + "§c.");
            player.closeInventory();
        }));
    }

    private void drawModeToggle(InventoryContents contents) {
        ItemStack it = new ItemStack(shapeless ? Material.SLIME_BALL : Material.REDSTONE);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(shapeless ? "§aMode: SANS FORME (Shapeless)" : "§bMode: AVEC FORME (Shaped)");
            m.setLore(List.of(
                    "§7Clique pour basculer.",
                    shapeless ? "§7L'ordre n'a pas d'importance." : "§7La disposition compte."
            ));
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
                m.setDisplayName("§6Permission requise");
                m.setLore(List.of(
                        "§7Noeud: §e" + permission,
                        "§7Clique pour rendre §aPublic"
                ));
            } else {
                m.setDisplayName("§aPublic");
                m.setLore(List.of(
                        "§7Aucune permission requise.",
                        "§7Clique pour exiger un noeud par défaut."
                ));
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
                recipeName,
                snapshotGrid(),
                result == null ? null : result.clone(),
                shapeless,
                permission // << save it
        );
        boolean ok = service.saveAndRegister(rec);
        if (ok) {
            String mode = shapeless ? "§aShapeless" : "§bShaped";
            String permMsg = (permission == null || permission.isBlank()) ? "§7(public)" : "§6perm §e" + permission;
            player.sendMessage("§a[OreoCraft] Recette §e" + recipeName + "§a enregistrée (" + mode + "§a) " + permMsg + ".");
        } else {
            player.sendMessage("§c[OreoCraft] Recette invalide (résultat + au moins 1 ingrédient).");
        }
    }

    private static ItemStack ui(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); it.setItemMeta(meta); }
        return it;
    }
}
