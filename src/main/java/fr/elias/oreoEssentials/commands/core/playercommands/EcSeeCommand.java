// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/EcSeeCommand.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.enderchest.EnderChestService;
import fr.elias.oreoEssentials.enderchest.EnderChestStorage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class EcSeeCommand implements OreoCommand, TabCompleter {
    @Override public String name() { return "ecsee"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.ecsee"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    @SuppressWarnings("deprecation")
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player viewer)) return true;

        if (!viewer.hasPermission("oreo.ecsee")) {
            viewer.sendMessage(ChatColor.RED + "You lack permission.");
            return true;
        }
        if (args.length < 1) {
            viewer.sendMessage(ChatColor.RED + "Usage: /ecsee <player>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            viewer.sendMessage(ChatColor.RED + "No data for that player.");
            return true;
        }

        // If online: open live EnderChest (edits apply instantly)
        if (target.isOnline() && target.getPlayer() != null) {
            viewer.openInventory(target.getPlayer().getEnderChest());
            viewer.sendMessage(ChatColor.GRAY + "Opened " + ChatColor.AQUA + target.getName() + ChatColor.GRAY + "'s ender chest.");
            return true;
        }

        // Offline: load via service & open a virtual view; save back on close
        OreoEssentials plugin = OreoEssentials.get();
        EnderChestService svc = plugin.getEnderChestService();

        int rows = 6; // admins see full 6 rows to avoid hiding items
        ItemStack[] contents = svc.loadFor(target.getUniqueId(), rows);
        Inventory inv = Bukkit.createInventory(
                viewer,
                rows * 9,
                "§5Ender Chest §7(" + (target.getName() != null ? target.getName() : target.getUniqueId()) + ")"
        );
        inv.setContents(EnderChestStorage.clamp(contents, rows));
        viewer.openInventory(inv);

        // one-shot close listener to persist to target on close
        UUID viewerId = viewer.getUniqueId();
        UUID targetId = target.getUniqueId();
        Listener l = new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onClose(InventoryCloseEvent e) {
                if (!(e.getPlayer() instanceof Player p)) return;
                if (!p.getUniqueId().equals(viewerId)) return;
                // Save whatever the admin left in the view
                svc.saveFor(targetId, rows, e.getInventory().getContents());
                HandlerList.unregisterAll(this);
                p.sendMessage(ChatColor.GREEN + "Saved changes to " + ChatColor.AQUA + (target.getName() != null ? target.getName() : targetId) + ChatColor.GREEN + "'s ender chest.");
            }
        };
        Bukkit.getPluginManager().registerEvents(l, plugin);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(p))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
