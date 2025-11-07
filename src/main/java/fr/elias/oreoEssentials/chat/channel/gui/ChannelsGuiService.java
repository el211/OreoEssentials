// File: src/main/java/fr/elias/oreoEssentials/chat/channel/gui/ChannelsGuiService.java
package fr.elias.oreoEssentials.chat.channel.gui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.chat.channel.ChannelManager;
import fr.elias.oreoEssentials.chat.channel.ChannelsConfig;
import fr.minuskube.inv.InventoryManager;
import fr.minuskube.inv.SmartInventory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

public class ChannelsGuiService implements TabExecutor {

    private final OreoEssentials plugin;
    private final ChannelManager cm;
    private final ChannelsConfig cfg;
    private final InventoryManager inv;

    public ChannelsGuiService(OreoEssentials plugin, ChannelManager cm, ChannelsConfig cfg) {
        this.plugin = plugin;
        this.cm = cm;
        this.cfg = cfg;
        this.inv = plugin.getInvManager(); // make sure you have a getter

        // 🔌 Wire the /channels command
        if (plugin.getCommand("channels") != null) {
            plugin.getCommand("channels").setExecutor(this);
            plugin.getCommand("channels").setTabCompleter(this);
        }
    }

    public void openMain(Player p) {
        SmartInventory.builder()
                .manager(inv)
                .id("channels-main")
                .provider(new ChannelsMainMenu(plugin, cm, cfg, this))
                .size(6, 9)
                .title("Chat Channels")
                .build()
                .open(p);
    }

    public void openActions(Player p, String channelId) {
        SmartInventory.builder()
                .manager(inv)
                .id("channels-actions-" + channelId.toLowerCase())
                .provider(new ChannelActionsMenu(plugin, cm, cfg, this, channelId))
                .size(4, 9)
                .title("Channel: " + channelId)
                .build()
                .open(p);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true; // ✅ return true so Bukkit doesn’t echo usage
        }
        if (!p.hasPermission("oreo.channels.gui.open")) {
            p.sendMessage("§cNo permission: oreo.channels.gui.open");
            return true;
        }
        openMain(p);
        return true; // ✅ important
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
