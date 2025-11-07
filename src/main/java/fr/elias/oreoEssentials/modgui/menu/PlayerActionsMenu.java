package fr.elias.oreoEssentials.modgui.menu;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.util.ItemBuilder;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PlayerActionsMenu implements InventoryProvider {
    private final OreoEssentials plugin;
    private final UUID target;
    /** Optional back action supplied by caller (can be null). */
    private final Runnable onBack;

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
                new ItemBuilder(Material.CHEST).name("&bInvsee").build(),
                e -> runPlayer(p, "invsee " + name))
        );
        c.set(2, 5, ClickableItem.of(
                new ItemBuilder(Material.ENDER_CHEST).name("&bEcSee").build(),
                e -> runPlayer(p, "ecsee " + name))
        );
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
