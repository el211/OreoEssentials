package fr.elias.oreoEssentials.customcraft;

import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.InventoryManager;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RecipeListMenu implements InventoryProvider {
    private final Plugin plugin;
    private final InventoryManager invMgr;
    private final CustomCraftingService service;

    private RecipeListMenu(Plugin plugin, InventoryManager invMgr, CustomCraftingService service) {
        this.plugin = plugin;
        this.invMgr = invMgr;
        this.service = service;
    }

    public static SmartInventory open(Player p, Plugin plugin, InventoryManager invMgr, CustomCraftingService service) {
        SmartInventory inv = SmartInventory.builder()
                .id("oecraft:browse")
                .provider(new RecipeListMenu(plugin, invMgr, service))
                .size(6, 9)
                .title("§bOreoCraft — Recipes")
                .manager(invMgr)
                .build();
        inv.open(p);
        return inv;
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        ItemStack border = ui(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int r = 0; r < 6; r++) for (int c = 0; c < 9; c++) contents.set(r, c, ClickableItem.empty(border));

        // Top bar
        contents.set(0, 4, ClickableItem.empty(ui(Material.BOOK, "§eClick a recipe to edit")));

        // Pagination
        Pagination pag = contents.pagination();
        List<ClickableItem> items = new ArrayList<>();

        for (String name : service.allNames()) {
            ItemStack icon = new ItemStack(Material.CRAFTING_TABLE);
            ItemMeta m = icon.getItemMeta();
            if (m != null) {
                String mode = service.get(name).map(r -> r.isShapeless() ? "Shapeless" : "Shaped").orElse("?");
                String perm = service.getPermissionFor(name).orElse("§7None");
                m.setDisplayName("§b" + name);
                m.setLore(List.of("§7Mode: §f" + mode, "§7Perm: §f" + perm, "§aClick to edit"));
                icon.setItemMeta(m);
            }
            items.add(ClickableItem.of(icon, (InventoryClickEvent e) -> {
                Player clicker = (Player) e.getWhoClicked();
                CraftDesignerMenu.build(plugin, invMgr, service, name).open(clicker);
            }));
        }

        pag.setItems(items.toArray(new ClickableItem[0]));
        pag.setItemsPerPage(28);

        // draw page (rows 1..4, cols 1..7)
        SlotIterator si = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1);
        si.blacklist(1, 0); si.blacklist(1, 8);
        si.blacklist(2, 0); si.blacklist(2, 8);
        si.blacklist(3, 0); si.blacklist(3, 8);
        si.blacklist(4, 0); si.blacklist(4, 8);
        pag.addToIterator(si);

        // Prev/Next (cast HumanEntity -> Player for getInventory)
        contents.set(5, 3, ClickableItem.of(ui(Material.ARROW, "§ePrevious"), (InventoryClickEvent e) -> {
            Player p = (Player) e.getWhoClicked();
            if (!pag.isFirst()) {
                invMgr.getInventory(p).ifPresent(inv -> inv.open(p, pag.getPage() - 1));
            }
        }));
        contents.set(5, 5, ClickableItem.of(ui(Material.ARROW, "§eNext"), (InventoryClickEvent e) -> {
            Player p = (Player) e.getWhoClicked();
            invMgr.getInventory(p).ifPresent(inv -> inv.open(p, pag.getPage() + 1));
        }));
    }

    @Override
    public void update(Player player, InventoryContents contents) { /* no-op */ }

    private static ItemStack ui(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); it.setItemMeta(meta); }
        return it;
    }
}
