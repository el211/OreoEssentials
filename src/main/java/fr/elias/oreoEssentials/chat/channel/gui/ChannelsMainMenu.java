// File: src/main/java/fr/elias/oreoEssentials/chat/channel/gui/ChannelsMainMenu.java
package fr.elias.oreoEssentials.chat.channel.gui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.chat.channel.ChatChannel;
import fr.elias.oreoEssentials.chat.channel.ChannelManager;
import fr.elias.oreoEssentials.chat.channel.ChannelsConfig;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ChannelsMainMenu implements InventoryProvider {

    private final OreoEssentials plugin;
    private final ChannelManager cm;
    private final ChannelsConfig cfg;
    private final ChannelsGuiService svc;

    public ChannelsMainMenu(OreoEssentials plugin, ChannelManager cm, ChannelsConfig cfg, ChannelsGuiService svc) {
        this.plugin = plugin;
        this.cm = cm;
        this.cfg = cfg;
        this.svc = svc;
    }

    @Override
    public void init(Player p, InventoryContents contents) {
        // Build a sorted list: autojoin first, then alpha
        List<String> ids = cfg.getAllChannelIds().stream()
                .sorted(Comparator
                        .comparing((String id) -> {
                            ChatChannel ch = cfg.get(id);
                            return ch != null && ch.isDefaultAutoJoin();
                        })
                        .reversed()
                        .thenComparing(String::compareToIgnoreCase))
                .collect(Collectors.toList());

        int row = 1, col = 1;
        for (String id : ids) {
            ChatChannel def = cfg.get(id);
            if (def == null) continue;

            boolean joined = safeIsJoined(p, id);
            String speak = safeGetSpeak(p);

            Material mat = joined ? Material.LIME_DYE : Material.GRAY_DYE;
            if (def.isReadOnly()) mat = Material.PAPER;         // readonly look
            if (def.isCrossServer()) mat = Material.CONDUIT;     // hint cross-server

            ItemStack it = new ItemStack(mat);
            ItemMeta im = it.getItemMeta();
            im.setDisplayName((joined ? ChatColor.GREEN : ChatColor.AQUA)
                    + def.getDisplay() + ChatColor.GRAY + " (" + id + ")");
            im.setLore(List.of(
                    ChatColor.DARK_GRAY + "Read-only: " + (def.isReadOnly() ? ChatColor.RED + "Yes" : ChatColor.GREEN + "No"),
                    ChatColor.DARK_GRAY + "Cross-server: " + (def.isCrossServer() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"),
                    "",
                    ChatColor.YELLOW + "Status: " + (joined ? ChatColor.GREEN + "Joined" : ChatColor.RED + "Not joined"),
                    ChatColor.YELLOW + "Speaking: " + (id.equalsIgnoreCase(speak) ? ChatColor.GOLD + "Yes" : ChatColor.DARK_GRAY + "No"),
                    "",
                    ChatColor.GRAY + "Click to manage…"
            ));
            it.setItemMeta(im);

            contents.set(row, col, ClickableItem.of(it, e -> svc.openActions(p, id)));

            col++;
            if (col >= 8) { col = 1; row++; }
            if (row >= 5) break; // fits 6x9 (with border spacing)
        }

        // Decorative border
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        gm.setDisplayName(" ");
        glass.setItemMeta(gm);
        for (int c = 0; c < 9; c++) {
            contents.set(0, c, ClickableItem.empty(glass));
            contents.set(5, c, ClickableItem.empty(glass));
        }
        for (int r = 1; r < 5; r++) {
            contents.set(r, 0, ClickableItem.empty(glass));
            contents.set(r, 8, ClickableItem.empty(glass));
        }
    }

    @Override public void update(Player p, InventoryContents contents) {}

    private boolean safeIsJoined(Player p, String channel) {
        try { return cm.isJoined(p, channel); } catch (Throwable t) { return false; }
    }
    private String safeGetSpeak(Player p) {
        try { return cm.getSpeakChannel(p); } catch (Throwable t) { return "local"; }
    }
}
