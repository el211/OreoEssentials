// File: src/main/java/fr/elias/oreoEssentials/chat/channel/gui/ChannelActionsMenu.java
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

import java.util.List;

public class ChannelActionsMenu implements InventoryProvider {

    private final OreoEssentials plugin;
    private final ChannelManager cm;
    private final ChannelsConfig cfg;
    private final ChannelsGuiService svc;
    private final String channelId;

    public ChannelActionsMenu(OreoEssentials plugin, ChannelManager cm, ChannelsConfig cfg,
                              ChannelsGuiService svc, String channelId) {
        this.plugin = plugin;
        this.cm = cm;
        this.cfg = cfg;
        this.svc = svc;
        this.channelId = channelId;
    }

    @Override
    public void init(Player p, InventoryContents contents) {
        ChatChannel def = cfg.get(channelId);
        if (def == null) {
            p.sendMessage(prefix() + ChatColor.RED + "Channel not found: " + channelId);
            svc.openMain(p);
            return;
        }

        boolean joined = safeIsJoined(p, channelId);
        String speak = safeGetSpeak(p);

        // Info card
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName(ChatColor.AQUA + def.getDisplay() + ChatColor.GRAY + " (" + channelId + ")");
        im.setLore(List.of(
                ChatColor.DARK_GRAY + "Read-only: " + (def.isReadOnly() ? ChatColor.RED + "Yes" : ChatColor.GREEN + "No"),
                ChatColor.DARK_GRAY + "Cross-server: " + (def.isCrossServer() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"),
                "",
                ChatColor.YELLOW + "Joined: " + (joined ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"),
                ChatColor.YELLOW + "Speaking: " + (channelId.equalsIgnoreCase(speak) ? ChatColor.GOLD + "Yes" : ChatColor.DARK_GRAY + "No")
        ));
        info.setItemMeta(im);
        contents.set(1, 4, ClickableItem.empty(info));

        // Join button
        ItemStack join = new ItemStack(joined ? Material.GRAY_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE);
        ItemMeta jm = join.getItemMeta();
        jm.setDisplayName((joined ? ChatColor.DARK_GRAY : ChatColor.GREEN) + "Join");
        jm.setLore(List.of(
                joined ? ChatColor.DARK_GRAY + "Already joined." :
                        ChatColor.GRAY + "Click to join this channel."
        ));
        join.setItemMeta(jm);
        contents.set(1, 2, ClickableItem.of(join, e -> {
            if (joined) {
                p.sendMessage(prefix() + ChatColor.RED + "You're already in " + ChatColor.YELLOW + channelId + ChatColor.RED + ".");
                return;
            }
            if (!hasPerm(p, def.getPermissionJoin())) {
                p.sendMessage(prefix() + ChatColor.RED + "You don't have permission to join this channel.");
                return;
            }
            boolean ok = safeJoin(p, channelId);
            if (ok) {
                p.sendMessage(prefix() + ChatColor.GREEN + "Joined " + ChatColor.AQUA + def.getDisplay() + ChatColor.GRAY + " (" + channelId + ").");
                svc.openActions(p, channelId);
            } else {
                p.sendMessage(prefix() + ChatColor.RED + "Failed to join channel.");
            }
        }));

        // Leave button
        ItemStack leave = new ItemStack(joined ? Material.RED_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta lm = leave.getItemMeta();
        lm.setDisplayName((joined ? ChatColor.RED : ChatColor.DARK_GRAY) + "Leave");
        lm.setLore(List.of(
                joined ? ChatColor.GRAY + "Click to leave this channel." :
                        ChatColor.DARK_GRAY + "You are not in this channel."
        ));
        leave.setItemMeta(lm);
        contents.set(1, 6, ClickableItem.of(leave, e -> {
            if (!joined) {
                p.sendMessage(prefix() + ChatColor.RED + "You cannot leave " + ChatColor.YELLOW + channelId + ChatColor.RED + " because you are not in it.");
                return;
            }
            if (!hasPerm(p, def.getPermissionLeave())) {
                p.sendMessage(prefix() + ChatColor.RED + "You don't have permission to leave this channel.");
                return;
            }
            boolean ok = safeLeave(p, channelId);
            if (ok) {
                p.sendMessage(prefix() + ChatColor.GREEN + "Left " + ChatColor.AQUA + def.getDisplay() + ChatColor.GRAY + " (" + channelId + ").");
                svc.openActions(p, channelId);
            } else {
                p.sendMessage(prefix() + ChatColor.RED + "Failed to leave channel.");
            }
        }));

        // Set speak button
        ItemStack speakBtn = new ItemStack(channelId.equalsIgnoreCase(speak) ? Material.COMPASS : Material.PAPER);
        ItemMeta sm = speakBtn.getItemMeta();
        sm.setDisplayName((channelId.equalsIgnoreCase(speak) ? ChatColor.GOLD : ChatColor.YELLOW) + "Set Speak");
        sm.setLore(List.of(
                def.isReadOnly()
                        ? ChatColor.RED + "This channel is read-only; you cannot speak here."
                        : (joined
                        ? (channelId.equalsIgnoreCase(speak)
                        ? ChatColor.DARK_GRAY + "Already speaking in this channel."
                        : ChatColor.GRAY + "Click to route your chat into this channel.")
                        : ChatColor.RED + "Join first to speak here.")
        ));
        speakBtn.setItemMeta(sm);
        contents.set(2, 4, ClickableItem.of(speakBtn, e -> {
            if (def.isReadOnly()) {
                p.sendMessage(prefix() + ChatColor.RED + "This channel is read-only.");
                return;
            }
            if (!joined) {
                p.sendMessage(prefix() + ChatColor.RED + "You must join the channel first.");
                return;
            }
            if (!hasPerm(p, def.getPermissionSpeak())) {
                p.sendMessage(prefix() + ChatColor.RED + "You don't have permission to speak in this channel.");
                return;
            }
            boolean ok = safeSetSpeak(p, channelId);
            if (ok) {
                p.sendMessage(prefix() + ChatColor.GREEN + "You now speak in " + ChatColor.AQUA + def.getDisplay() + ChatColor.GRAY + " (" + channelId + ").");
                svc.openActions(p, channelId);
            } else {
                p.sendMessage(prefix() + ChatColor.RED + "Failed to set speak channel.");
            }
        }));

        // Back
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(ChatColor.YELLOW + "Back");
        back.setItemMeta(bm);
        contents.set(2, 2, ClickableItem.of(back, e -> svc.openMain(p)));
    }

    @Override public void update(Player p, InventoryContents contents) {}

    private String prefix() {
        try {
            String raw = plugin.getChatConfig().getCustomConfig().getString("channels.messages.prefix", "&8[&bChannels&8]&r ");
            return ChatColor.translateAlternateColorCodes('&', raw);
        } catch (Throwable t) {
            return "§8[§bChannels§8] §r";
        }
    }

    private boolean hasPerm(Player p, String perm) {
        return perm == null || perm.isEmpty() || p.hasPermission(perm);
    }

    // ---- Safe wrappers calling ChannelManager API ----
    private boolean safeIsJoined(Player p, String id) {
        try { return cm.isJoined(p, id); } catch (Throwable t) { return false; }
    }
    private boolean safeJoin(Player p, String id) {
        try { return cm.join(p, id); } catch (Throwable t) { return false; }
    }
    private boolean safeLeave(Player p, String id) {
        try { return cm.leave(p, id); } catch (Throwable t) { return false; }
    }
    private boolean safeSetSpeak(Player p, String id) {
        try { return cm.setSpeak(p, id); } catch (Throwable t) { return false; }
    }
    private String safeGetSpeak(Player p) {
        try { return cm.getSpeakChannel(p); } catch (Throwable t) { return "local"; }
    }
}
