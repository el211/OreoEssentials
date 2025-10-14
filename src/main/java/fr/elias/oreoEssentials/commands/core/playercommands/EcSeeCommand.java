package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.enderchest.EnderChestService;
import fr.elias.oreoEssentials.enderchest.EnderChestStorage;
import fr.elias.oreoEssentials.util.Uuids;
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

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class EcSeeCommand implements OreoCommand, TabCompleter {
    @Override public String name() { return "ecsee"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.ecsee"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return true; }

    private static final int EC_SIZE = 27; // 3 rows

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player viewer)) return true;

        if (!viewer.hasPermission(permission())) {
            viewer.sendMessage(ChatColor.RED + "You lack permission.");
            return true;
        }
        if (args.length < 1) {
            viewer.sendMessage(ChatColor.RED + "Usage: /ecsee <player>");
            return true;
        }

        Logger log = OreoEssentials.get().getLogger();

        // Prefer exact online name (works better with Geyser/Floodgate)
        Player onlineByName = Bukkit.getPlayerExact(args[0]);
        UUID targetId;
        String targetName;

        if (onlineByName != null && onlineByName.isOnline()) {
            targetId = onlineByName.getUniqueId();
            targetName = onlineByName.getName();
        } else {
            targetId = Uuids.resolve(args[0]);
            if (targetId == null) {
                viewer.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }
            OfflinePlayer off = Bukkit.getOfflinePlayer(targetId);
            targetName = (off.getName() != null) ? off.getName() : targetId.toString();
        }

        EnderChestService svc = Bukkit.getServicesManager().load(EnderChestService.class);
        if (svc == null) {
            viewer.sendMessage(ChatColor.RED + "Ender chest storage is not configured.");
            return true;
        }

        // ---- Decide best source for contents ----
        ItemStack[] contents = new ItemStack[EC_SIZE];
        String source = "unknown";
        Player live = Bukkit.getPlayer(targetId);

        if (live != null && live.isOnline()) {
            boolean viewingVirtual =
                    live.getOpenInventory() != null
                            && EnderChestService.TITLE.equals(live.getOpenInventory().getTitle());

            if (viewingVirtual) {
                // If the player currently has YOUR virtual EC GUI open — read from that GUI
                contents = Arrays.copyOf(
                        live.getOpenInventory().getTopInventory().getContents(),
                        EC_SIZE
                );
                source = "LIVE_VIRTUAL_GUI";
            } else {
                // Use your service snapshot (virtual EC) so we see the same data as /ec
                contents = EnderChestStorage.clamp(svc.loadFor(targetId, 3), 3);
                source = "SERVICE_SNAPSHOT_ONLINE";
            }

            // Optional: compare vanilla EC just for diagnostics
            ItemStack[] vanilla = Arrays.copyOf(live.getEnderChest().getContents(), EC_SIZE);
            int vanillaNonEmpty = countNonEmpty(vanilla);
            log.info("[ECSEE] Online target=" + targetName
                    + " source=" + source
                    + " snapshotNonEmpty=" + countNonEmpty(contents)
                    + " vanillaNonEmpty=" + vanillaNonEmpty);
        } else {
            // Player is offline → read from storage
            contents = EnderChestStorage.clamp(svc.loadFor(targetId, 3), 3);
            source = "SERVICE_SNAPSHOT_OFFLINE";
            log.info("[ECSEE] Offline target=" + targetName
                    + " source=" + source
                    + " snapshotNonEmpty=" + countNonEmpty(contents));
        }

        // ---- Open proxy GUI ----
        Inventory gui = Bukkit.createInventory(
                null,
                EC_SIZE,
                ChatColor.DARK_PURPLE + "Ender Chest " + ChatColor.GRAY + "(" + targetName + ")"
        );
        gui.setContents(EnderChestStorage.clamp(contents, 3));
        viewer.openInventory(gui);

        // ---- Save on close ----
        UUID viewerId = viewer.getUniqueId();
        String finalSource = source;

        Listener l = new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onClose(InventoryCloseEvent e) {
                if (!(e.getPlayer() instanceof Player p)) return;
                if (!p.getUniqueId().equals(viewerId)) return;
                if (e.getInventory() != gui) return;

                ItemStack[] edited = Arrays.copyOf(gui.getContents(), EC_SIZE);

                // Persist (so cross-server & future joins see changes)
                svc.saveFor(targetId, 3, edited);

                // If target is online on THIS server and has the virtual EC open, mirror to that GUI
                Player liveNow = Bukkit.getPlayer(targetId);
                if (liveNow != null && liveNow.isOnline()) {
                    try {
                        boolean viewingVirtual =
                                liveNow.getOpenInventory() != null
                                        && EnderChestService.TITLE.equals(liveNow.getOpenInventory().getTitle());

                        if (viewingVirtual) {
                            for (int i = 0; i < EC_SIZE; i++) {
                                liveNow.getOpenInventory().getTopInventory().setItem(i, edited[i]);
                            }
                            // Make sure the service writes whatever we reflected
                            svc.saveFromInventory(liveNow, liveNow.getOpenInventory().getTopInventory());
                            log.info("[ECSEE] Mirrored changes to target's VIRTUAL GUI. target=" + targetName);
                        } else {
                            // As a fallback you can also write vanilla EC; it won’t hurt if unused
                            liveNow.getEnderChest().setContents(edited);
                            log.info("[ECSEE] Wrote changes to target's VANILLA EC. target=" + targetName);
                        }
                    } catch (Throwable t) {
                        log.warning("[ECSEE] Failed to push live changes for " + targetName + ": " + t.getMessage());
                    }
                }

                log.info("[ECSEE] Saved " + countNonEmpty(edited) + " non-empty slots for " + targetName
                        + " (initialSource=" + finalSource + ")");
                HandlerList.unregisterAll(this);
                p.sendMessage(ChatColor.GREEN + "Saved changes to " + ChatColor.AQUA + targetName + ChatColor.GREEN + "'s ender chest.");
            }
        };
        Bukkit.getPluginManager().registerEvents(l, OreoEssentials.get());
        return true;
    }

    private static int countNonEmpty(ItemStack[] arr) {
        int c = 0;
        if (arr == null) return 0;
        for (ItemStack it : arr) if (it != null && it.getType() != org.bukkit.Material.AIR) c++;
        return c;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
