package fr.elias.oreoEssentials.playtime;

import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class PrewardsMenu implements InventoryProvider {

    private final PlaytimeRewardsService svc;

    public PrewardsMenu(PlaytimeRewardsService svc) {
        this.svc = svc;
    }

    public SmartInventory inventory(Player p) {
        return SmartInventory.builder()
                .manager(svc.getPlugin().getInvManager()) // SmartInvs manager from your plugin
                .id("prewards:" + p.getUniqueId())
                .provider(this)
                .title(svc.color(svc.skin.title))
                .size(Math.max(1, svc.skin.rows), 9)
                .build();
    }

    @Override
    public void init(Player p, InventoryContents contents) {
        // ---- Background ----
        if (svc.skin.fillEmpty) {
            ItemStack fill = new ItemStack(svc.skin.fillerMat);
            ItemMeta im = fill.getItemMeta();
            if (im != null) {
                im.setDisplayName(svc.color(svc.skin.fillerName));
                if (svc.skin.fillerCmd > 0) im.setCustomModelData(svc.skin.fillerCmd);
                im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_DYE);
                fill.setItemMeta(im);
            }
            contents.fill(ClickableItem.empty(fill));
        }

        // Iterator starts at (row=1, col=1) to leave a 1-block border
        SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1);

        // ---- Rewards ----
        svc.rewards.values().forEach(r -> {
            if (!svc.hasPermission(p, r)) return;

            PlaytimeRewardsService.State state = svc.stateOf(p, r);

            // Base icon
            Material baseMat = (r.iconMaterial != null ? r.iconMaterial : Material.PAPER);
            String baseName  = (r.iconName != null ? r.iconName : r.displayName);

            // Build and color lore safely
            List<String> lore = new ArrayList<>();
            if (r.iconLore != null) {
                for (String line : r.iconLore) {
                    lore.add(svc.color(line)); // translate '&' codes
                }
            }

            ItemStack item = new ItemStack(baseMat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (r.customModelData != null) meta.setCustomModelData(r.customModelData);
                meta.setDisplayName(svc.color(baseName));
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

                // Apply global skin overrides for the reward's current state
                PlaytimeRewardsService.SkinState skinState = svc.skin.states.get(state.name());
                if (skinState != null) {
                    if (skinState.mat != null) {
                        item.setType(skinState.mat); // override material if provided
                    }
                    if (skinState.name != null && !skinState.name.isEmpty()) {
                        meta.setDisplayName(svc.color(skinState.name));
                    }
                    if (skinState.glow) {
                        meta.addEnchant(Enchantment.UNBREAKING, 1, true); // visual glow
                    }
                }

                // Append state line
                lore.add(svc.color("&7State: &f" + state.name()));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }

            ClickableItem clickable = ClickableItem.of(item, e -> {
                // Recompute state on click (in case time passed)
                PlaytimeRewardsService.State now = svc.stateOf(p, r);
                if (now == PlaytimeRewardsService.State.READY) {
                    boolean ok = svc.claim(p, r.id, true);
                    if (ok) {
                        // Refresh GUI to reflect new state
                        inventory(p).open(p);
                    } else {
                        p.sendMessage(svc.color("&cNot ready or invalid reward."));
                    }
                } else {
                    p.sendMessage(svc.color("&cNot ready. Keep playing!"));
                }
            });

            // Positioning: fixed slot or auto-flow with iterator
            if (r.slot != null) {
                int row = Math.max(0, Math.min(svc.skin.rows - 1, r.slot / 9));
                int col = Math.max(0, Math.min(8, r.slot % 9));
                contents.set(row, col, clickable);
            } else {
                it.set(clickable); // advances the iterator automatically
            }
        });
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        // No periodic updates; GUI is refreshed after a claim
    }
}
