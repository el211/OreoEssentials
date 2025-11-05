package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.services.WarpService;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

import static org.bukkit.ChatColor.*;

public class ConfirmDeleteWarpGui implements InventoryProvider {

    private final WarpService warps;
    private final String warpName;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    public ConfirmDeleteWarpGui(WarpService warps, String warpName, Runnable onConfirm, Runnable onCancel) {
        this.warps = warps;
        this.warpName = warpName;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    public static void open(Player p, WarpService warps, String warpName, Runnable onConfirm, Runnable onCancel) {
        SmartInventory.builder()
                .id("oreo:warps:confirm")
                .provider(new ConfirmDeleteWarpGui(warps, warpName, onConfirm, onCancel))
                .size(3, 9)
                .title(ChatColor.DARK_RED + "Delete '" + warpName + "'?")
                .manager(fr.elias.oreoEssentials.OreoEssentials.get().getInvManager())
                .build()
                .open(p);
    }

    @Override
    public void init(Player p, InventoryContents contents) {
        contents.set(0, 4, fr.minuskube.inv.ClickableItem.empty(infoItem(warpName)));

        contents.set(SlotPos.of(1, 3), fr.minuskube.inv.ClickableItem.of(
                actionItem(Material.GREEN_CONCRETE, GREEN + "Yes, delete"),
                e -> {
                    boolean ok = warps.delWarp(warpName.toLowerCase());
                    if (ok) {
                        p.sendMessage(ChatColor.RED + "Deleted warp " + ChatColor.YELLOW + warpName + ChatColor.RED + ".");
                    } else {
                        p.sendMessage(ChatColor.RED + "Failed to delete warp " + ChatColor.YELLOW + warpName + ChatColor.RED + ".");
                    }
                    p.closeInventory();
                    if (onConfirm != null) onConfirm.run();
                }));

        contents.set(SlotPos.of(1, 5), fr.minuskube.inv.ClickableItem.of(
                actionItem(Material.RED_CONCRETE, RED + "No, cancel"),
                e -> {
                    p.closeInventory();
                    if (onCancel != null) onCancel.run();
                }));
    }

    @Override public void update(Player player, InventoryContents contents) {}

    private ItemStack infoItem(String name) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Delete Warp");
            meta.setLore(List.of(ChatColor.GRAY + "Are you sure you want to delete",
                    ChatColor.YELLOW + name + ChatColor.GRAY + "?"));
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack actionItem(Material mat, String title) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(title);
            it.setItemMeta(meta);
        }
        return it;
    }
}
