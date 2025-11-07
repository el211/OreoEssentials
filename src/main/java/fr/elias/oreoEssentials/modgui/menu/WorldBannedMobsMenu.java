package fr.elias.oreoEssentials.modgui.menu;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.ModGuiService;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class WorldBannedMobsMenu implements InventoryProvider {
    private final OreoEssentials plugin;
    private final ModGuiService svc;
    private final World world;

    private static final List<EntityType> CATALOG = Arrays.asList(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER,
            EntityType.ENDERMAN, EntityType.SLIME, EntityType.WITCH, EntityType.PHANTOM,
            EntityType.BLAZE, EntityType.MAGMA_CUBE, EntityType.WITHER_SKELETON
    );

    private static final Map<EntityType, Material> EGG = Map.ofEntries(
            Map.entry(EntityType.ZOMBIE,           Material.ZOMBIE_SPAWN_EGG),
            Map.entry(EntityType.SKELETON,         Material.SKELETON_SPAWN_EGG),
            Map.entry(EntityType.CREEPER,          Material.CREEPER_SPAWN_EGG),
            Map.entry(EntityType.SPIDER,           Material.SPIDER_SPAWN_EGG),
            Map.entry(EntityType.ENDERMAN,         Material.ENDERMAN_SPAWN_EGG),
            Map.entry(EntityType.SLIME,            Material.SLIME_SPAWN_EGG),
            Map.entry(EntityType.WITCH,            Material.WITCH_SPAWN_EGG),
            Map.entry(EntityType.PHANTOM,          Material.PHANTOM_SPAWN_EGG),
            Map.entry(EntityType.BLAZE,            Material.BLAZE_SPAWN_EGG),
            Map.entry(EntityType.MAGMA_CUBE,       Material.MAGMA_CUBE_SPAWN_EGG),
            Map.entry(EntityType.WITHER_SKELETON,  Material.WITHER_SKELETON_SPAWN_EGG)
    );

    public WorldBannedMobsMenu(OreoEssentials plugin, ModGuiService svc, World world) {
        this.plugin = plugin; this.svc = svc; this.world = world;
    }

    @Override public void init(Player p, InventoryContents c) {
        int row = 1, col = 1;

        for (EntityType t : CATALOG) {
            boolean banned = svc.cfg().isMobBanned(world, t.name());

            Material icon = EGG.getOrDefault(t, Material.BARRIER);
            String pretty = capitalize(t.name().replace('_', ' '));

            ItemStack item = new ItemBuilder(icon)
                    .name((banned ? "&c" : "&a") + pretty)
                    .lore(
                            "&7World: &f" + world.getName(),
                            "&7Status: " + (banned ? "&cBANNED" : "&aALLOWED"),
                            "&8Click to toggle"
                    )
                    .build();

            if (banned) addGlow(item);

            c.set(row, col, ClickableItem.of(
                    item,
                    e -> {
                        // toggle (void), then re-check:
                        svc.cfg().toggleMobBan(world, t.name());
                        boolean nowBanned = svc.cfg().isMobBanned(world, t.name());

                        if (nowBanned) {
                            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.8f, 0.7f);
                            p.sendMessage("§c" + pretty + " §7is now §cBANNED §7in §f" + world.getName());
                        } else {
                            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                            p.sendMessage("§a" + pretty + " §7is now §aALLOWED §7in §f" + world.getName());
                        }
                        init(p, c); // refresh
                    }
            ));

            if (++col >= 8) { col = 1; row++; if (row >= 5) break; }
        }
    }

    @Override public void update(Player player, InventoryContents contents) {}

    private static void addGlow(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        // Preferred on Paper 1.20.5+ (Purpur inherits it):
        try {
            // Method exists on modern Paper: setEnchantmentGlintOverride(Boolean)
            ItemMeta.class.getMethod("setEnchantmentGlintOverride", Boolean.class)
                    .invoke(meta, Boolean.TRUE);
            stack.setItemMeta(meta);
            return;
        } catch (Throwable ignored) {
            // Fallback for Spigot/Paper older than 1.20.5: hidden enchant trick
        }

        // Fallback: add a hidden enchant to force the glint
        stack.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
        meta = stack.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        String[] parts = s.toLowerCase().split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return out.toString().trim();
    }
}
