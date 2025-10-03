// File: src/main/java/fr/elias/oreoEssentials/kits/KitsMenuSI.java
package fr.elias.oreoEssentials.kits;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Kits GUI using SmartInvs. All player-facing text and sounds are pulled from lang.yml via Lang helper.
 */
public class KitsMenuSI implements InventoryProvider {

    private final OreoEssentials plugin;
    private final KitsManager manager;

    public KitsMenuSI(OreoEssentials plugin, KitsManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /** Open the SmartInvs menu for a player. */
    public static void open(OreoEssentials plugin, KitsManager manager, Player player) {
        int rows = Math.max(1, Math.min(6, manager.getMenuRows()));

        SmartInventory.builder()
                .id("oreo_kits_menu")
                .title(manager.getMenuTitle()) // title comes from kits.yml menu.title
                .size(rows, 9)
                .provider(new KitsMenuSI(plugin, manager))
                .manager(plugin.getInvManager())
                .build()
                .open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        // Fill background if configured
        if (manager.isMenuFill()) {
            Material m = Material.matchMaterial(manager.getFillMaterial());
            if (m == null) m = Material.GRAY_STAINED_GLASS_PANE;
            ItemStack filler = new ItemStack(m);
            ItemMeta fim = filler.getItemMeta();
            if (fim != null) { fim.setDisplayName(" "); filler.setItemMeta(fim); }
            contents.fill(ClickableItem.empty(filler));
        }

        // Build ClickableItems for kits
        List<ClickableItem> buttons = new ArrayList<>();
        List<Kit> kitList = new ArrayList<>(manager.getKits().values()); // stable ordering

        for (Kit kit : kitList) {
            ItemStack icon = kit.getIcon() != null ? kit.getIcon().clone() : new ItemStack(Material.CHEST);
            ItemMeta meta = icon.getItemMeta();

            long left = manager.getSecondsLeft(player, kit);

            // Lore text from lang.yml
            // kits.gui.lore.claimable / kits.gui.lore.on-cooldown
            String loreKey = (left > 0 ? "kits.gui.lore.on-cooldown" : "kits.gui.lore.claimable");
            String loreStr = Lang.msg(
                    loreKey,
                    Map.of(
                            "kit_name", kit.getDisplayName(),
                            "cooldown_left", Lang.timeHuman(left),
                            "cooldown_left_raw", String.valueOf(left)
                    ),
                    player
            );

            List<String> loreLines = new ArrayList<>();
            if (!loreStr.isEmpty()) {
                for (String line : loreStr.split("\n")) loreLines.add(line);
            }

            if (meta != null) {
                meta.setDisplayName(kit.getDisplayName());
                meta.setLore(loreLines);
                icon.setItemMeta(meta);
            }

            buttons.add(ClickableItem.of(icon, e -> {
                long cdLeft = manager.getSecondsLeft(player, kit);

                // Cooldown gate (unless bypass)
                if (cdLeft > 0 && !player.hasPermission("oreo.kit.bypasscooldown")) {
                    // denied sound from lang.yml
                    if (Lang.getBool("kits.gui.sounds.denied.enabled", true)) {
                        try {
                            Sound s = Sound.valueOf(Lang.get("kits.gui.sounds.denied.sound", "BLOCK_NOTE_BLOCK_BASS"));
                            float vol = (float) Lang.getDouble("kits.gui.sounds.denied.volume", 0.7);
                            float pit = (float) Lang.getDouble("kits.gui.sounds.denied.pitch", 0.7);
                            player.playSound(player.getLocation(), s, vol, pit);
                        } catch (Throwable ignored) {}
                    }

                    // cooldown message from lang.yml
                    Lang.send(
                            player,
                            "kits.cooldown",
                            Map.of(
                                    "kit_name", kit.getDisplayName(),
                                    "cooldown_left", Lang.timeHuman(cdLeft),
                                    "cooldown_left_raw", String.valueOf(cdLeft)
                            ),
                            player
                    );

                    // refresh to update lore if any changed
                    contents.inventory().open(player);
                    return;
                }

                boolean handled = manager.claim(player, kit.getId());

                // claim sound from lang.yml
                if (handled && Lang.getBool("kits.gui.sounds.claim.enabled", true)) {
                    try {
                        Sound s = Sound.valueOf(Lang.get("kits.gui.sounds.claim.sound", "ENTITY_EXPERIENCE_ORB_PICKUP"));
                        float vol = (float) Lang.getDouble("kits.gui.sounds.claim.volume", 0.8);
                        float pit = (float) Lang.getDouble("kits.gui.sounds.claim.pitch", 1.2);
                        player.playSound(player.getLocation(), s, vol, pit);
                    } catch (Throwable ignored) {}
                }

                // refresh menu to update cooldown lore
                contents.inventory().open(player);
            }));
        }

        int rows = contents.inventory().getRows();
        int cols = contents.inventory().getColumns();

        boolean anyFixed = manager.getKits().values().stream().anyMatch(k -> k.getSlot() != null);

        if (anyFixed) {
            // 1) Place all fixed-slot kits first
            for (int i = 0; i < kitList.size(); i++) {
                Kit k = kitList.get(i);
                if (k.getSlot() == null) continue;

                int slot = k.getSlot();
                if (slot < 0 || slot >= rows * cols) continue;

                int r = slot / cols;
                int c = slot % cols;

                contents.set(SlotPos.of(r, c), buttons.get(i));
            }

            // 2) Flow the rest into any empty positions
            int next = 0;
            outer:
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (contents.get(SlotPos.of(r, c)).isPresent()) continue;

                    // advance to next non-fixed item
                    while (next < kitList.size()) {
                        Kit k = kitList.get(next);
                        ClickableItem ci = buttons.get(next);
                        next++;
                        if (k.getSlot() == null) {
                            contents.set(SlotPos.of(r, c), ci);
                            break;
                        }
                    }
                    if (next >= kitList.size()) break outer;
                }
            }
        } else {
            // No fixed slots: use Pagination + SlotIterator to auto-flow
            Pagination pagination = contents.pagination();
            pagination.setItems(buttons.toArray(new ClickableItem[0]));
            pagination.setItemsPerPage(rows * cols);

            // Start from top-left; allow override so filler can be replaced
            SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 0, 0);
            it.allowOverride(true);
            pagination.addToIterator(it);
        }
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        // We reopen on click; nothing needed here per tick.
    }
}
