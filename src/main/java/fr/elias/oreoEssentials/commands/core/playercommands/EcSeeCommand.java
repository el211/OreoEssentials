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

        final var plugin = OreoEssentials.get();
        final Logger log = plugin.getLogger();
        final boolean debug = plugin.getConfig().getBoolean("debug", false);

        UUID targetId = resolveTargetId(args[0]);
        if (targetId == null) {
            viewer.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }

        String targetName;
        Player online = Bukkit.getPlayer(targetId);
        if (online != null) {
            targetName = online.getName();
        } else {
            OfflinePlayer off = Bukkit.getOfflinePlayer(targetId);
            targetName = (off.getName() != null ? off.getName() : args[0]);
        }

        EnderChestService svc = Bukkit.getServicesManager().load(EnderChestService.class);
        if (svc == null) {
            viewer.sendMessage(ChatColor.RED + "Ender chest storage is not configured.");
            return true;
        }

        // ---- Decide best source for contents ----
        ItemStack[] contents;
        String source;
        Player live = Bukkit.getPlayer(targetId);

        if (live != null && live.isOnline()) {
            boolean viewingVirtual = live.getOpenInventory() != null
                    && EnderChestService.TITLE.equals(live.getOpenInventory().getTitle());

            if (viewingVirtual) {
                contents = Arrays.copyOf(live.getOpenInventory().getTopInventory().getContents(), EC_SIZE);
                source = "LIVE_VIRTUAL_GUI";
            } else {
                contents = EnderChestStorage.clamp(svc.loadFor(targetId, 3), 3);
                source = "SERVICE_SNAPSHOT_ONLINE";
            }

            if (debug) {
                ItemStack[] vanilla = Arrays.copyOf(live.getEnderChest().getContents(), EC_SIZE);
                int vanillaNonEmpty = countNonEmpty(vanilla);
                log.info("[ECSEE] Online target=" + targetName
                        + " source=" + source
                        + " snapshotNonEmpty=" + countNonEmpty(contents)
                        + " vanillaNonEmpty=" + vanillaNonEmpty);
            }
        } else {
            contents = EnderChestStorage.clamp(svc.loadFor(targetId, 3), 3);
            source = "SERVICE_SNAPSHOT_OFFLINE";
            if (debug) {
                log.info("[ECSEE] Offline target=" + targetName
                        + " source=" + source
                        + " snapshotNonEmpty=" + countNonEmpty(contents));
            }
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

                // If target is online on THIS server, try to mirror live view
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
                            svc.saveFromInventory(liveNow, liveNow.getOpenInventory().getTopInventory());
                            if (debug) log.info("[ECSEE] Mirrored changes to target's VIRTUAL GUI. target=" + targetName);
                        } else {
                            liveNow.getEnderChest().setContents(edited);
                            if (debug) log.info("[ECSEE] Wrote changes to target's VANILLA EC. target=" + targetName);
                        }
                    } catch (Throwable t) {
                        log.warning("[ECSEE] Failed to push live changes for " + targetName + ": " + t.getMessage());
                    }
                }

                if (debug) {
                    log.info("[ECSEE] Saved " + countNonEmpty(edited) + " non-empty slots for " + targetName
                            + " (initialSource=" + finalSource + ")");
                }
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

    /** Minimal, API-safe resolver: exact online → UUID string → plugin resolver. */
    private static UUID resolveTargetId(String arg) {
        Player p = Bukkit.getPlayerExact(arg);
        if (p != null) return p.getUniqueId();

        // Try parsing as UUID
        try { return UUID.fromString(arg); } catch (IllegalArgumentException ignored) {}

        // Add this block to check the global player directory (MongoDB)
        UUID global = OreoEssentials.get().getPlayerDirectory().lookupUuidByName(arg);
        if (global != null) return global;

        // Final fallback: your old resolver (Floodgate, etc)
        return fr.elias.oreoEssentials.util.Uuids.resolve(arg);
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
