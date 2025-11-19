package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.cross.InvBridge;
import fr.elias.oreoEssentials.services.InventoryService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class InvseeCommand implements OreoCommand, TabCompleter {
    @Override public String name() { return "invsee"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.invsee"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return true; }

    private static final int GUI_SIZE = 54;
    private static final int HOTBAR_START = 0;   // 0..8
    private static final int MAIN_START   = 9;   // 9..35
    private static final int ARMOR_START  = 45;  // 45..48
    private static final int OFFHAND_SLOT = 49;  // 49

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player viewer)) return true;

        if (!viewer.hasPermission(permission())) {
            viewer.sendMessage(ChatColor.RED + "You lack permission.");
            return true;
        }
        if (args.length < 1) {
            viewer.sendMessage(ChatColor.RED + "Usage: /invsee <player>");
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

        InventoryService svc = Bukkit.getServicesManager().load(InventoryService.class);
        if (svc == null) {
            viewer.sendMessage(ChatColor.RED + "Offline inventory storage is not configured.");
            return true;
        }

        boolean crossServerInv = plugin.getSettings().getBoolean(
                "features.cross-server.invsee",
                plugin.getConfig().getBoolean("crossserverinv", false)
        );        boolean allowRemoteEdits = plugin.getConfig().getBoolean("invsee.allow-edit-while-online-elsewhere", true);
        InvBridge bridge = plugin.getInvBridge(); // may be null if Rabbit disabled

        Player liveHere = Bukkit.getPlayer(targetId);
        boolean onThisServer = (liveHere != null && liveHere.isOnline());

        // Editable if: target is here OR cross-server is off OR we explicitly allow remote edits
        boolean editable = onThisServer || (!crossServerInv) || allowRemoteEdits;

        // ---- Build GUI ----
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE,
                ChatColor.AQUA + "Inventory " + ChatColor.GRAY + "(" + targetName + ")");

        // ---- Choose snapshot source ----
        InventoryService.Snapshot snap;
        String source;

        if (onThisServer) {
            PlayerInventory pi = liveHere.getInventory();
            snap = new InventoryService.Snapshot();
            snap.contents = Arrays.copyOf(pi.getContents(), 41);
            snap.armor    = Arrays.copyOf(pi.getArmorContents(), 4);
            snap.offhand  = pi.getItemInOffHand();
            source = "LIVE_LOCAL";
        } else if (crossServerInv && bridge != null) {
            snap = bridge.requestLiveInv(targetId);
            if (snap != null && (hasAny(snap.contents) || hasAny(snap.armor) || notAir(snap.offhand))) {
                source = "LIVE_REMOTE_BRIDGE";
            } else {
                snap = svc.load(targetId);
                if (snap == null) snap = new InventoryService.Snapshot();
                source = "STORAGE_FALLBACK";
            }
        } else {
            snap = svc.load(targetId);
            if (snap == null) snap = new InventoryService.Snapshot();
            source = "STORAGE_ONLY";
        }

        if (debug) {
            log.info("[INVSEE] Open: target=" + targetName + " source=" + source
                    + " contNonEmpty=" + countNonEmpty(snap.contents)
                    + " armorNonEmpty=" + countNonEmpty(snap.armor)
                    + " offhand=" + notAir(snap.offhand)
                    + " editable=" + editable);
        }

        // Populate GUI
        ItemStack[] cont = pad(snap.contents, 41);
        for (int i = 0; i < 9; i++) gui.setItem(HOTBAR_START + i, cont[i]);
        for (int i = 9; i < 36; i++) gui.setItem(MAIN_START + (i - 9), cont[i]);
        ItemStack[] armor = pad(snap.armor, 4);
        for (int i = 0; i < 4; i++) gui.setItem(ARMOR_START + i, armor[i]);
        gui.setItem(OFFHAND_SLOT, snap.offhand);

        viewer.openInventory(gui);

        // Read-only guard if we don't want remote edits
        if (!editable) {
            viewer.sendMessage(ChatColor.YELLOW + "Read-only view: player may be online on another server. "
                    + "Edits are disabled to prevent duplication.");
            UUID viewerId = viewer.getUniqueId();

            Listener readOnly = new Listener() {
                @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
                public void onClick(InventoryClickEvent e) {
                    if (!(e.getWhoClicked() instanceof Player p)) return;
                    if (!p.getUniqueId().equals(viewerId)) return;
                    if (e.getView().getTopInventory() != gui) return;
                    e.setCancelled(true);
                }
                @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
                public void onDrag(InventoryDragEvent e) {
                    if (!(e.getWhoClicked() instanceof Player p)) return;
                    if (!p.getUniqueId().equals(viewerId)) return;
                    if (e.getView().getTopInventory() != gui) return;
                    e.setCancelled(true);
                }
                @EventHandler(priority = EventPriority.MONITOR)
                public void onClose(InventoryCloseEvent e) {
                    if (!(e.getPlayer() instanceof Player p)) return;
                    if (!p.getUniqueId().equals(viewerId)) return;
                    if (e.getInventory() != gui) return;
                    HandlerList.unregisterAll(this);
                }
            };
            Bukkit.getPluginManager().registerEvents(readOnly, plugin);
            return true;
        }

        // Editable: save back on close (apply live locally OR via bridge)
        UUID viewerId = viewer.getUniqueId();
        Listener editor = new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onClose(InventoryCloseEvent e) {
                if (!(e.getPlayer() instanceof Player p)) return;
                if (!p.getUniqueId().equals(viewerId)) return;
                if (e.getInventory() != gui) return;

                InventoryService.Snapshot edited = new InventoryService.Snapshot();

                ItemStack[] c = new ItemStack[41];
                for (int i = 0; i < 9; i++) c[i] = gui.getItem(HOTBAR_START + i);
                for (int i = 9; i < 36; i++) c[i] = gui.getItem(MAIN_START + (i - 9));
                edited.contents = c;

                edited.armor = new ItemStack[4];
                for (int i = 0; i < 4; i++) edited.armor[i] = gui.getItem(ARMOR_START + i);
                edited.offhand = gui.getItem(OFFHAND_SLOT);

                boolean applied = false;

                Player liveNow = Bukkit.getPlayer(targetId);
                if (liveNow != null && liveNow.isOnline()) {
                    try {
                        PlayerInventory pi = liveNow.getInventory();
                        ItemStack[] main = Arrays.copyOf(edited.contents, pi.getContents().length);
                        pi.setContents(main);
                        pi.setArmorContents(Arrays.copyOf(edited.armor, 4));
                        pi.setItemInOffHand(edited.offhand);
                        applied = true;
                        if (debug) log.info("[INVSEE] Applied LIVE (local) changes to " + targetName);
                    } catch (Throwable t) {
                        log.warning("[INVSEE] Local live apply failed for " + targetName + ": " + t.getMessage());
                    }
                } else if (crossServerInv && bridge != null) {
                    try {
                        boolean ok = bridge.applyLiveInv(targetId, edited);
                        applied = ok;
                        if (debug) log.info("[INVSEE] Remote apply via bridge for " + targetName + " -> " + ok);
                    } catch (Throwable t) {
                        log.warning("[INVSEE] Remote apply error for " + targetName + ": " + t.getMessage());
                    }
                }

                // Persist snapshot regardless (so future joins / non-live cases get it)
                try {
                    svc.save(targetId, edited);
                    if (debug) {
                        log.info("[INVSEE] Saved snapshot for " + targetName
                                + " cont=" + countNonEmpty(edited.contents)
                                + " armor=" + countNonEmpty(edited.armor)
                                + " offhand=" + notAir(edited.offhand));
                    }
                } catch (Throwable t) {
                    log.warning("[INVSEE] Failed saving snapshot for " + targetName + ": " + t.getMessage());
                }

                if (!applied && crossServerInv) {
                    p.sendMessage(ChatColor.YELLOW + "Note: The player may be online on another server; "
                            + "your changes were saved to storage but might not have applied live.");
                }

                HandlerList.unregisterAll(this);
                p.sendMessage(ChatColor.GREEN + "Saved changes to " + ChatColor.AQUA + targetName + ChatColor.GREEN + "'s inventory.");
            }
        };
        Bukkit.getPluginManager().registerEvents(editor, plugin);
        return true;
    }

    private static boolean hasAny(ItemStack[] arr) { return countNonEmpty(arr) > 0; }
    private static boolean notAir(ItemStack it) { return it != null && it.getType() != org.bukkit.Material.AIR; }
    private static int countNonEmpty(ItemStack[] arr) {
        int c = 0; if (arr == null) return 0;
        for (ItemStack it : arr) if (it != null && it.getType() != org.bukkit.Material.AIR) c++;
        return c;
    }
    private static ItemStack[] pad(ItemStack[] src, int size) {
        return Arrays.copyOf(src == null ? new ItemStack[size] : src, size);
    }

    /** Minimal, API-safe resolver: exact online → UUID string → plugin resolver. */
    /** Minimal, API-safe resolver: exact online → UUID string → PlayerDirectory → fallback. */
    private static UUID resolveTargetId(String arg) {
        // 1) Exact online name on this server
        Player p = Bukkit.getPlayerExact(arg);
        if (p != null) return p.getUniqueId();

        // 2) Try parsing as UUID
        try {
            return UUID.fromString(arg);
        } catch (IllegalArgumentException ignored) { }

        // 3) Network-wide via PlayerDirectory (Mongo-backed)
        try {
            var plugin = OreoEssentials.get();
            var dir = plugin.getPlayerDirectory();
            if (dir != null) {
                UUID global = dir.lookupUuidByName(arg);
                if (global != null) return global;
            }
        } catch (Throwable ignored) { }

        // 4) Final fallback: your old resolver (Floodgate, etc.)
        return fr.elias.oreoEssentials.util.Uuids.resolve(arg);
    }


    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      org.bukkit.command.Command cmd,
                                      String alias,
                                      String[] args) {
        if (args.length != 1) return List.of();

        String partial = args[0];
        String want = partial.toLowerCase(Locale.ROOT);

        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        // 1) Local online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            String n = p.getName();
            if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) {
                out.add(n);
            }
        }

        // 2) Network-wide via PlayerDirectory.suggestOnlineNames()
        var plugin = OreoEssentials.get();
        var dir = plugin.getPlayerDirectory();
        if (dir != null) {
            try {
                var names = dir.suggestOnlineNames(want, 50);
                if (names != null) {
                    for (String n : names) {
                        if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) {
                            out.add(n);
                        }
                    }
                }
            } catch (Throwable ignored) { }
        }

        // limit to 50 suggestions to keep tab output sane
        return out.stream().limit(50).toList();
    }

}
