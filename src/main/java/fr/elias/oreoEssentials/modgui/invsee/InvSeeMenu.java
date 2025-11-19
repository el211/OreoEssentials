// package: fr.elias.oreoEssentials.modgui.invsee
package fr.elias.oreoEssentials.modgui.invsee;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.cross.InvBridge;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.elias.oreoEssentials.services.InventoryService;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.InventoryListener;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.UUID;

public class InvSeeMenu implements InventoryProvider {

    private final OreoEssentials plugin;
    private final UUID targetId;

    public InvSeeMenu(OreoEssentials plugin, UUID targetId) {
        this.plugin = plugin;
        this.targetId = targetId;
    }

    /**
     * Helper to open with a close listener that pushes changes
     * back to the *live* player across the network using InvBridge.
     */
    public static void open(OreoEssentials plugin, Player viewer, UUID targetId) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
        String targetName = (op.getName() != null ? op.getName() : targetId.toString().substring(0, 8));

        plugin.getLogger().info("[INVSEE-DEBUG] open(): viewer=" + viewer.getName()
                + " targetId=" + targetId + " targetName=" + targetName);

        SmartInventory.builder()
                .manager(plugin.getInvManager())
                .provider(new InvSeeMenu(plugin, targetId))
                .title("§8Inventory of " + targetName)
                .size(6, 9)
                .closeable(true)
                .listener(new InventoryListener<>(InventoryCloseEvent.class, ev -> {
                    Player staff = (Player) ev.getPlayer();
                    Inventory top = ev.getInventory();
                    plugin.getLogger().info("[INVSEE-DEBUG] InventoryCloseEvent for viewer="
                            + staff.getName() + " targetId=" + targetId
                            + " topSize=" + top.getSize());
                    syncAndApply(plugin, staff, targetId, top);
                }))
                .build()
                .open(viewer);
    }

    @Override
    public void init(Player viewer, InventoryContents c) {
        plugin.getLogger().info("[INVSEE-DEBUG] init(): viewer=" + viewer.getName()
                + " targetId=" + targetId + " on server=" + plugin.getServerNameSafe());

        InvBridge bridge = plugin.getInvBridge();
        if (bridge == null) {
            plugin.getLogger().warning("[INVSEE-DEBUG] init(): InvBridge is null, aborting.");
            viewer.closeInventory();
            viewer.sendMessage("§cCross-server inventory bridge is not available.");
            return;
        }

        // Request live snapshot from whoever *currently* has the player.
        InventoryService.Snapshot snap = bridge.requestLiveInv(targetId);
        if (snap == null) {
            plugin.getLogger().warning("[INVSEE-DEBUG] init(): requestLiveInv returned NULL for targetId=" + targetId);
            viewer.closeInventory();
            viewer.sendMessage("§cCould not fetch live inventory. " +
                    "Player may be offline or on an unreachable server.");
            return;
        }

        plugin.getLogger().info("[INVSEE-DEBUG] init(): got snapshot for targetId=" + targetId
                + " contentsLen=" + (snap.contents == null ? "null" : snap.contents.length)
                + " armorLen=" + (snap.armor == null ? "null" : snap.armor.length)
                + " offhand=" + (snap.offhand == null ? "null" : snap.offhand.getType().name()));

        if (snap.contents == null) snap.contents = new ItemStack[41];
        if (snap.armor == null)    snap.armor    = new ItemStack[4];

        // Layout inside the 6x9 GUI:

        // 1) Main contents
        for (int i = 0; i < 41; i++) {
            int row = i / 9;
            int col = i % 9;
            ItemStack it = snap.contents[i];

            c.set(row, col, ClickableItem.of(
                    (it == null ? new ItemStack(Material.AIR) : it),
                    e -> {} // editing is handled by SmartInvs
            ));
        }

        // 2) Armor (slots 45..48 => row 5, col 0..3)
        for (int i = 0; i < 4; i++) {
            int raw = 45 + i;
            int row = raw / 9; // 5
            int col = raw % 9;

            ItemStack it = (snap.armor.length > i ? snap.armor[i] : null);
            c.set(row, col, ClickableItem.of(
                    (it == null ? new ItemStack(Material.AIR) : it),
                    e -> {}
            ));
        }

        // 3) Offhand (slot 49 => row 5, col 4)
        {
            int raw = 49;
            int row = raw / 9; // 5
            int col = raw % 9;

            ItemStack off = snap.offhand;
            c.set(row, col, ClickableItem.of(
                    (off == null ? new ItemStack(Material.AIR) : off),
                    e -> {}
            ));
        }

        // Label/description item
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
        String targetName = (op.getName() != null ? op.getName() : targetId.toString().substring(0, 8));

        c.set(5, 8, ClickableItem.empty(
                new ItemBuilder(Material.BOOK)
                        .name("§bEditing inventory of §e" + targetName)
                        .lore("§7Changes will be pushed to",
                                "§7the live player across the network.")
                        .build()
        ));

        plugin.getLogger().info("[INVSEE-DEBUG] init(): GUI prepared for viewer=" + viewer.getName()
                + " targetId=" + targetId);
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        // No live animation needed
    }

    /**
     * Read the GUI back into an InventoryService.Snapshot
     * and push it via InvBridge.applyLiveInv.
     */
    public static void syncAndApply(OreoEssentials plugin,
                                    Player viewer,
                                    UUID targetId,
                                    Inventory inv) {
        plugin.getLogger().info("[INVSEE-DEBUG] syncAndApply(): viewer=" + viewer.getName()
                + " targetId=" + targetId + " invSize=" + inv.getSize());

        InvBridge bridge = plugin.getInvBridge();
        if (bridge == null) {
            plugin.getLogger().warning("[INVSEE-DEBUG] syncAndApply(): InvBridge is null, aborting.");
            viewer.sendMessage("§cCannot apply inventory changes: InvBridge unavailable.");
            return;
        }

        InventoryService.Snapshot snap = new InventoryService.Snapshot();
        snap.contents = new ItemStack[41];
        snap.armor    = new ItemStack[4];

        // contents 0..40 from slots 0..40
        for (int i = 0; i < 41; i++) {
            ItemStack it = inv.getItem(i);
            snap.contents[i] = (it == null || it.getType() == Material.AIR) ? null : it.clone();
        }

        // armor from slots 45..48
        for (int i = 0; i < 4; i++) {
            int raw = 45 + i;
            ItemStack it = inv.getItem(raw);
            snap.armor[i] = (it == null || it.getType() == Material.AIR) ? null : it.clone();
        }

        // offhand from slot 49
        {
            ItemStack it = inv.getItem(49);
            snap.offhand = (it == null || it.getType() == Material.AIR) ? null : it.clone();
        }

        // ---- SAFETY: don't push a fully empty snapshot (likely request failure) ----
        boolean allEmpty = true;
        int nonEmptyContents = 0;
        int nonEmptyArmor = 0;
        boolean offhandNonEmpty = false;

        for (ItemStack it : snap.contents) {
            if (it != null && it.getType() != Material.AIR) {
                allEmpty = false;
                nonEmptyContents++;
            }
        }
        if (allEmpty) {
            for (ItemStack it : snap.armor) {
                if (it != null && it.getType() != Material.AIR) {
                    allEmpty = false;
                    nonEmptyArmor++;
                }
            }
        } else {
            for (ItemStack it : snap.armor) {
                if (it != null && it.getType() != Material.AIR) {
                    nonEmptyArmor++;
                }
            }
        }
        if (snap.offhand != null && snap.offhand.getType() != Material.AIR) {
            allEmpty = false;
            offhandNonEmpty = true;
        }

        plugin.getLogger().info("[INVSEE-DEBUG] syncAndApply(): nonEmptyContents=" + nonEmptyContents
                + " nonEmptyArmor=" + nonEmptyArmor
                + " offhandNonEmpty=" + offhandNonEmpty);

        if (allEmpty) {
            plugin.getLogger().warning("[INVSEE-DEBUG] syncAndApply(): snapshot fully empty, NOT applying. targetId=" + targetId);
            viewer.sendMessage("§cNot applying an empty inventory snapshot " +
                    "(remote inventory probably failed to load).");
            return;
        }

        boolean ok = bridge.applyLiveInv(targetId, snap);
        plugin.getLogger().info("[INVSEE-DEBUG] syncAndApply(): applyLiveInv returned " + ok
                + " for targetId=" + targetId);

        if (!ok) {
            viewer.sendMessage("§cFailed to apply inventory remotely. " +
                    "Player may be offline or on an unreachable server.");
        } else {
            viewer.sendMessage("§aInventory updated across the network.");
        }

        logChange(plugin, viewer, targetId);
    }

    private static void logChange(OreoEssentials plugin, Player staff, UUID targetId) {
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
            String targetName = (op.getName() != null ? op.getName() : targetId.toString());

            var file = new java.io.File(plugin.getDataFolder(), "playeractions.log");
            try (PrintWriter out = new PrintWriter(new FileWriter(file, true))) {
                out.println(LocalDateTime.now() + " [INVSEE] "
                        + staff.getName() + " edited inventory of " + targetName);
            }
        } catch (Exception ignored) {
        }
    }
}
