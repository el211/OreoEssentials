package fr.elias.oreoEssentials.modgui.menu;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.ecsee.EcSeeMenu;
import fr.elias.oreoEssentials.modgui.inspect.PlayerInspectMenu;
import fr.elias.oreoEssentials.modgui.ip.IpAltsMenu;
import fr.elias.oreoEssentials.modgui.notes.NotesChatListener;
import fr.elias.oreoEssentials.modgui.notes.PlayerNotesManager;
import fr.elias.oreoEssentials.modgui.notes.PlayerNotesMenu;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import fr.minuskube.inv.InventoryListener;
import org.bukkit.inventory.Inventory;
import fr.elias.oreoEssentials.modgui.invsee.InvSeeMenu;

import java.util.UUID;

public class PlayerActionsMenu implements InventoryProvider {
    private final OreoEssentials plugin;
    private final UUID target;
    /** Optional back action supplied by caller (can be null). */
    private final Runnable onBack;
    private PlayerNotesManager notesManager;
    private NotesChatListener notesChat;
    public PlayerActionsMenu(OreoEssentials plugin, UUID target) {
        this(plugin, target, null);
    }
    public PlayerActionsMenu(OreoEssentials plugin, UUID target, Runnable onBack) {
        this.plugin = plugin;
        this.target = target;
        this.onBack = onBack;
    }

    @Override
    public void init(Player p, InventoryContents c) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(target);
        final String name = (op != null && op.getName() != null) ? op.getName() : target.toString();

        // Back button (optional)
        if (onBack != null) {
            c.set(0, 0, ClickableItem.of(
                    new ItemBuilder(Material.ARROW).name("&7&l← Back").lore("&7Return to previous menu").build(),
                    e -> onBack.run()
            ));
        }

        c.set(1, 2, ClickableItem.of(
                new ItemBuilder(Material.BARRIER).name("&cBan").lore("&7Temp example: 1d (reason: ModGUI)").build(),
                e -> runConsole("ban " + name + " 1d ModGUI"))
        );
        c.set(1, 3, ClickableItem.of(
                new ItemBuilder(Material.PAPER).name("&eMute").lore("&7Example: 10m (reason: ModGUI)").build(),
                e -> runConsole("mute " + name + " 10m ModGUI"))
        );
        c.set(1, 4, ClickableItem.of(
                new ItemBuilder(Material.GREEN_DYE).name("&aUnmute").build(),
                e -> runConsole("unmute " + name))
        );
        c.set(1, 5, ClickableItem.of(
                new ItemBuilder(Material.OAK_DOOR).name("&6Kick").build(),
                e -> kick(name, "Kicked by staff via ModGUI"))
        );
        c.set(1, 6, ClickableItem.of(
                new ItemBuilder(Material.TOTEM_OF_UNDYING).name("&aHeal").build(),
                e -> runPlayer(p, "heal " + name))
        );

        c.set(2, 2, ClickableItem.of(
                new ItemBuilder(Material.COOKED_BEEF).name("&eFeed").build(),
                e -> runPlayer(p, "feed " + name))
        );
        c.set(2, 3, ClickableItem.of(
                new ItemBuilder(Material.IRON_SWORD).name("&cKill").build(),
                e -> runPlayer(p, "kill " + name))
        );
        c.set(2, 4, ClickableItem.of(
                new ItemBuilder(Material.CHEST)
                        .name("&bInvsee (network)")
                        .lore("&7View and edit inventory",
                                "&7across all servers via RabbitMQ.")
                        .build(),
                e -> InvSeeMenu.open(plugin, p, target)
        ));

        c.set(2, 5, ClickableItem.of(
                new ItemBuilder(Material.ENDER_CHEST).name("&bEcSee (logged)").build(),
                e -> {
                    SmartInventory.builder()
                            .manager(plugin.getInvManager())
                            .provider(new EcSeeMenu(plugin, target))
                            .title("§8EnderChest: " + name)
                            .size(6, 9)
                            .closeable(true)
                            .listener(new InventoryListener<>(InventoryCloseEvent.class, ev -> {
                                Inventory inv = ev.getInventory();              // inventory that was just closed
                                Player staff = (Player) ev.getPlayer();         // viewer who edited
                                // target may be on another server; EcSeeMenu.syncAndLog uses cross-server EC storage
                                EcSeeMenu.syncAndLog(plugin, staff, target, inv);
                            }))
                            .build()
                            .open(p);
                }
        ));

        c.set(2, 6, ClickableItem.of(
                new ItemBuilder(Material.CLOCK).name("&9Freeze 60s").build(),
                e -> runConsole("freeze " + name + " 60"))
        );

        c.set(3, 3, ClickableItem.of(
                new ItemBuilder(Material.ENDER_EYE).name("&5Vanish toggle").build(),
                e -> runPlayer(p, "vanish " + name))
        );
        c.set(3, 5, ClickableItem.of(
                new ItemBuilder(Material.NETHER_STAR).name("&dGamemode cycle (S/C/SP)").build(),
                e -> cycleGamemode(name))
        );

        c.set(4, 4, ClickableItem.empty(
                new ItemBuilder(Material.BOOK)
                        .name("&7Stats (placeholder)")
                        .lore("&7Add your own stats view here")
                        .build()
        ));
        // between other c.set(...) in row 3 for example
        c.set(3, 4, ClickableItem.of(
                new ItemBuilder(Material.CLOCK).name("&9Freeze 60s / Unfreeze").build(),
                e -> {
                    var fm = plugin.getFreezeManager();
                    if (fm == null) return;
                    Player t = Bukkit.getPlayer(target);
                    if (t == null) {
                        p.sendMessage("§cTarget is offline.");
                        return;
                    }
                    if (fm.isFrozen(target)) {
                        fm.unfreeze(t);
                        p.sendMessage("§aUnfroze §f" + t.getName());
                    } else {
                        fm.freeze(t, p, 60L);
                        p.sendMessage("§cFroze §f" + t.getName() + " §cfor §e60s");
                    }
                }
        ));
        // Notes button, for example row 4,col 2
        c.set(4, 2, ClickableItem.of(
                new ItemBuilder(Material.WRITABLE_BOOK)
                        .name("&ePlayer notes")
                        .lore("&7View / add staff notes.")
                        .build(),
                e -> SmartInventory.builder()
                        .manager(plugin.getInvManager())
                        .provider(new PlayerNotesMenu(plugin,
                                plugin.getNotesManager(),
                                plugin.getNotesChat(),
                                target))
                        .title("§8Notes: " + name)
                        .size(6, 9)
                        .build()
                        .open(p)
        ));
        c.set(4, 6, ClickableItem.of(
                new ItemBuilder(Material.COMPASS)
                        .name("&eIP & Alts")
                        .lore("&7View last IP and potential alts.")
                        .build(),
                e -> fr.minuskube.inv.SmartInventory.builder()
                        .manager(plugin.getInvManager())
                        .provider(new IpAltsMenu(plugin, plugin.getIpTracker(), target))
                        .title("§8IP & Alts: " + name)
                        .size(6, 9)
                        .build()
                        .open(p)
        ));
        c.set(3, 4, ClickableItem.of(
                new ItemBuilder(Material.SPYGLASS)
                        .name("&bLive inspector")
                        .lore("&7View live stats (health, ping, TPS...)")
                        .build(),
                e -> fr.minuskube.inv.SmartInventory.builder()
                        .manager(plugin.getInvManager())
                        .provider(new PlayerInspectMenu(plugin, target))
                        .title("§8Inspect: " + name)
                        .size(6, 9)
                        .build()
                        .open(p)
        ));
        c.set(2, 5, ClickableItem.of(
                new ItemBuilder(Material.ENDER_CHEST).name("&bEcSee (logged)").build(),
                e -> {
                    SmartInventory.builder()
                            .manager(plugin.getInvManager())
                            .provider(new EcSeeMenu(plugin, target))
                            .title("§8EnderChest: " + name)
                            .size(6, 9) // full 54 slots
                            .closeable(true)
                            .listener(new InventoryListener<>(InventoryCloseEvent.class, ev -> {
                                Inventory inv = ev.getInventory();
                                Player staff = (Player) ev.getPlayer();
                                EcSeeMenu.syncAndLog(plugin, staff, target, inv);
                            }))
                            .build()
                            .open(p);
                }
        ));


    }

    private void runPlayer(Player sender, String cmd) {
        // Run as player (uses their perms)
        sender.performCommand(cmd);
    }

    private void runConsole(String cmd) {
        // Run as console for staffy commands
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    private void kick(String name, String reason) {
        runConsole("kick " + name + " " + reason);
    }

    private void cycleGamemode(String name) {
        Player t = Bukkit.getPlayerExact(name);
        if (t == null) return;
        GameMode next = switch (t.getGameMode()) {
            case SURVIVAL -> GameMode.CREATIVE;
            case CREATIVE -> GameMode.SPECTATOR;
            default -> GameMode.SURVIVAL;
        };
        t.setGameMode(next);
        t.sendMessage("§eYour gamemode is now §6" + next);
    }

    @Override public void update(Player p, InventoryContents contents) {}
}
