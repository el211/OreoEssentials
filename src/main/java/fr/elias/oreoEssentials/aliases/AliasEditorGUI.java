// File: src/main/java/fr/elias/oreoEssentials/aliases/AliasEditorGUI.java
package fr.elias.oreoEssentials.aliases;

import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.InventoryManager;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public final class AliasEditorGUI {

    private final AliasService service;
    private final InventoryManager invManager;

    public AliasEditorGUI(AliasService service, InventoryManager invManager) {
        this.service = service;
        this.invManager = invManager;
    }

    public void openMain(Player viewer) {
        SmartInventory.builder()
                .size(6, 9)
                .title("Aliases (" + service.all().size() + ")")
                .provider(new MainProvider(service, invManager))
                .manager(invManager) // IMPORTANT
                .build()
                .open(viewer);
    }

    /* ------------------------- Providers ------------------------- */

    private static final class MainProvider implements InventoryProvider {
        private final AliasService service;
        private final InventoryManager invManager;

        MainProvider(AliasService service, InventoryManager invManager) {
            this.service = service;
            this.invManager = invManager;
        }

        @Override public void init(Player player, InventoryContents contents) {
            contents.fillRow(0, ClickableItem.empty(makePanel(Material.GRAY_STAINED_GLASS_PANE, " ")));
            contents.set(0, 4, ClickableItem.empty(makeNamed(Material.BOOK, "§dAlias Editor", List.of(
                    "§7Click an alias below to edit",
                    "§8Use §f/aliaseditor create §8to add new"
            ))));

            List<AliasService.AliasDef> defs = service.all().values().stream()
                    .sorted(Comparator.comparing(a -> a.name))
                    .collect(Collectors.toList());

            Pagination pagination = contents.pagination();
            ClickableItem[] items = defs.stream().map(def -> {
                List<String> lore = new ArrayList<>();
                lore.add("§7Run-as: §f" + def.runAs);
                lore.add("§7Enabled: " + (def.enabled ? "§atrue" : "§cfalse"));
                lore.add("§7Cooldown: §f" + def.cooldownSeconds + "s");
                lore.add("§7Checks: §f" + (def.checks == null ? 0 : def.checks.size())
                        + " §8(" + (def.logic == null ? "AND" : def.logic.name()) + ")");
                lore.add("§7Commands: §f" + def.commands.size());
                lore.add(" ");
                lore.add("§eClick to edit");
                ItemStack it = makeNamed(def.enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                        "§f/" + def.name, lore);
                return ClickableItem.of(it, e -> new DetailProvider(service, invManager, def.name).open(player));
            }).toArray(ClickableItem[]::new);

            pagination.setItems(items);
            pagination.setItemsPerPage(28);

            pagination.addToIterator(contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1)
                    .blacklist(1, 0).blacklist(1, 8)
                    .blacklist(2, 0).blacklist(2, 8)
                    .blacklist(3, 0).blacklist(3, 8)
                    .blacklist(4, 0).blacklist(4, 8));

            contents.set(5, 3, ClickableItem.of(makeNamed(Material.ARROW, "§ePrevious Page", List.of()),
                    e -> contents.inventory().open(player, pagination.previous().getPage())));
            contents.set(5, 5, ClickableItem.of(makeNamed(Material.ARROW, "§eNext Page", List.of()),
                    e -> contents.inventory().open(player, pagination.next().getPage())));
        }

        @Override public void update(Player player, InventoryContents contents) {}
    }

    private static final class DetailProvider implements InventoryProvider {
        private final AliasService service;
        private final InventoryManager invManager;
        private final String aliasName;

        DetailProvider(AliasService service, InventoryManager invManager, String aliasName) {
            this.service = service;
            this.invManager = invManager;
            this.aliasName = aliasName.toLowerCase(java.util.Locale.ROOT);
        }

        void open(Player p) {
            SmartInventory.builder()
                    .size(5, 9)
                    .title("Edit /" + aliasName)
                    .provider(this)
                    .manager(invManager) // IMPORTANT
                    .build()
                    .open(p);
        }

        @Override public void init(Player p, InventoryContents c) {
            AliasService.AliasDef def = service.get(aliasName);
            if (def == null) { p.closeInventory(); p.sendMessage("§cAlias not found."); return; }

            c.fillRow(0, ClickableItem.empty(makePanel(Material.GRAY_STAINED_GLASS_PANE, " ")));
            c.set(0, 4, ClickableItem.empty(makeNamed(Material.NAME_TAG, "§f/" + def.name, List.of(
                    "§7Run-as: §f" + def.runAs,
                    "§7Enabled: " + (def.enabled ? "§atrue" : "§cfalse"),
                    "§7Cooldown: §f" + def.cooldownSeconds + "s",
                    "§7Checks: §f" + (def.checks == null ? 0 : def.checks.size())
                            + " §8(" + (def.logic == null ? "AND" : def.logic.name()) + ")",
                    "§7Commands: §f" + def.commands.size()
            ))));

            c.set(2, 2, ClickableItem.of(
                    makeNamed(def.enabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                            def.enabled ? "§aEnabled" : "§cDisabled",
                            List.of("§7Click to toggle")),
                    e -> { String sub = def.enabled ? "disable" : "enable";
                        p.performCommand("aliaseditor " + sub + " " + def.name);
                        reopen(p); }
            ));

            c.set(2, 4, ClickableItem.of(
                    makeNamed(Material.COMMAND_BLOCK, "§eRun-as: §f" + def.runAs, List.of(
                            "§7Click to toggle to " + (def.runAs == AliasService.RunAs.PLAYER ? "§fCONSOLE" : "§fPLAYER"))),
                    e -> { String next = (def.runAs == AliasService.RunAs.PLAYER) ? "CONSOLE" : "PLAYER";
                        p.performCommand("aliaseditor runas " + def.name + " " + next);
                        reopen(p); }
            ));

            c.set(2, 6, ClickableItem.of(
                    makeNamed(Material.CLOCK, "§bCooldown: §f" + def.cooldownSeconds + "s", List.of(
                            "§7Click to cycle (0→5→30→60→0)")),
                    e -> { int next = nextCooldown(def.cooldownSeconds);
                        p.performCommand("aliaseditor cooldown " + def.name + " " + next);
                        reopen(p); }
            ));

            c.set(3, 3, ClickableItem.of(
                    makeNamed(Material.WRITABLE_BOOK, "§dChecks (" + (def.checks == null ? 0 : def.checks.size()) + ")", List.of(
                            "§7View / remove checks",
                            "§7Add with: §f/aliaseditor addcheck " + def.name + " <expr>")),
                    e -> new ChecksProvider(service, invManager, aliasName).open(p)
            ));

            c.set(3, 5, ClickableItem.of(
                    makeNamed(Material.BOOK, "§dCommands (" + def.commands.size() + ")", List.of(
                            "§7View lines",
                            "§7Add with: §f/aliaseditor addline " + def.name + " <command...>",
                            "§7Replace all: §f/aliaseditor set " + def.name + " <command...>")),
                    e -> new CommandsProvider(service, invManager, aliasName).open(p)
            ));

            c.set(4, 0, ClickableItem.of(makeNamed(Material.ARROW, "§eBack", List.of()),
                    e -> new MainProvider(service, invManager).init(p, c)));
        }

        @Override public void update(Player player, InventoryContents contents) {}

        private void reopen(Player p) { open(p); }
    }

    private static final class ChecksProvider implements InventoryProvider {
        private final AliasService service;
        private final InventoryManager invManager;
        private final String alias;

        ChecksProvider(AliasService service, InventoryManager invManager, String alias) {
            this.service = service; this.invManager = invManager; this.alias = alias;
        }

        void open(Player p) {
            SmartInventory.builder()
                    .size(6, 9)
                    .title("Checks /" + alias)
                    .provider(this)
                    .manager(invManager) // IMPORTANT
                    .build()
                    .open(p);
        }

        @Override public void init(Player p, InventoryContents c) {
            c.fillRow(0, ClickableItem.empty(makePanel(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ")));

            AliasService.AliasDef def = service.get(alias);
            if (def == null) { p.closeInventory(); p.sendMessage("§cAlias not found."); return; }

            c.set(0, 4, ClickableItem.empty(makeNamed(Material.PAPER, "§dChecks Help", List.of(
                    "§7Add: §f/aliaseditor addcheck " + alias + " <expr>",
                    "§7Examples:",
                    "§7  %ping%>=100",
                    "§7  permission:vip.kit",
                    "§7  %world%<-lobby-",
                    "§7Delete: click an item below"
            ))));

            c.set(5, 4, ClickableItem.of(
                    makeNamed(Material.COMPARATOR, "§bLogic: §f" + (def.logic == null ? "AND" : def.logic.name()),
                            List.of("§7Click to toggle AND/OR")),
                    e -> { String next = (def.logic == AliasService.LogicType.OR) ? "AND" : "OR";
                        p.performCommand("aliaseditor setlogic " + alias + " " + next);
                        open(p); }
            ));

            List<ClickableItem> items = new ArrayList<>();
            for (int i = 0; i < def.checks.size(); i++) {
                int displayIndex = i + 1; // effectively final for lambda
                AliasService.Check ch = def.checks.get(i);
                String label = "§f" + displayIndex + ") " + ch.expr;
                ClickableItem item = ClickableItem.of(
                        makeNamed(Material.MAP, label, List.of("§cClick to delete this check")),
                        e -> { p.performCommand("aliaseditor delcheck " + alias + " " + displayIndex);
                            open(p); });
                items.add(item);
            }

            Pagination pag = c.pagination();
            pag.setItems(items.toArray(ClickableItem[]::new));
            pag.setItemsPerPage(28);

            pag.addToIterator(c.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1)
                    .blacklist(1, 0).blacklist(1, 8)
                    .blacklist(2, 0).blacklist(2, 8)
                    .blacklist(3, 0).blacklist(3, 8)
                    .blacklist(4, 0).blacklist(4, 8));

            c.set(5, 3, ClickableItem.of(makeNamed(Material.ARROW, "§ePrevious Page", List.of()),
                    e -> c.inventory().open(p, pag.previous().getPage())));
            c.set(5, 5, ClickableItem.of(makeNamed(Material.ARROW, "§eNext Page", List.of()),
                    e -> c.inventory().open(p, pag.next().getPage())));

            c.set(5, 0, ClickableItem.of(makeNamed(Material.ARROW, "§eBack", List.of()),
                    e -> new DetailProvider(service, invManager, alias).open(p)));
        }

        @Override public void update(Player player, InventoryContents contents) {}
    }

    private static final class CommandsProvider implements InventoryProvider {
        private final AliasService service;
        private final InventoryManager invManager;
        private final String alias;

        CommandsProvider(AliasService service, InventoryManager invManager, String alias) {
            this.service = service; this.invManager = invManager; this.alias = alias;
        }

        void open(Player p) {
            SmartInventory.builder()
                    .size(6, 9)
                    .title("Commands /" + alias)
                    .provider(this)
                    .manager(invManager) // IMPORTANT
                    .build()
                    .open(p);
        }

        @Override public void init(Player p, InventoryContents c) {
            c.fillRow(0, ClickableItem.empty(makePanel(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ")));
            AliasService.AliasDef def = service.get(alias);
            if (def == null) { p.closeInventory(); p.sendMessage("§cAlias not found."); return; }

            c.set(0, 4, ClickableItem.empty(makeNamed(Material.PAPER, "§dCommand Lines", List.of(
                    "§7Add line: §f/aliaseditor addline " + alias + " <command...>",
                    "§7Replace all: §f/aliaseditor set " + alias + " <command...>",
                    "§7Tip: Use ';' inside a line to chain multiple commands"
            ))));

            List<ClickableItem> items = new ArrayList<>();
            int idx = 1;
            for (String line : def.commands) {
                String label = "§f" + idx + ") " + line;
                ClickableItem item = ClickableItem.of(
                        makeNamed(Material.OAK_SIGN, label, List.of(
                                "§7Click to put §f/aliaseditor set " + alias + " " + line + " §7in your chat",
                                "§7Then edit and send to replace all lines.")),
                        e -> { p.closeInventory();
                            p.sendMessage("§eEdit then run: §f/aliaseditor set " + alias + " " + line); });
                items.add(item);
                idx++;
            }

            Pagination pag = c.pagination();
            pag.setItems(items.toArray(ClickableItem[]::new));
            pag.setItemsPerPage(28);
            pag.addToIterator(c.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1)
                    .blacklist(1, 0).blacklist(1, 8)
                    .blacklist(2, 0).blacklist(2, 8)
                    .blacklist(3, 0).blacklist(3, 8)
                    .blacklist(4, 0).blacklist(4, 8));

            c.set(5, 3, ClickableItem.of(makeNamed(Material.ARROW, "§ePrevious Page", List.of()),
                    e -> c.inventory().open(p, pag.previous().getPage())));
            c.set(5, 5, ClickableItem.of(makeNamed(Material.ARROW, "§eNext Page", List.of()),
                    e -> c.inventory().open(p, pag.next().getPage())));
            c.set(5, 0, ClickableItem.of(makeNamed(Material.ARROW, "§eBack", List.of()),
                    e -> new DetailProvider(service, invManager, alias).open(p)));
        }

        @Override public void update(Player player, InventoryContents contents) {}
    }

    /* ------------------------- Utils ------------------------- */

    private static int nextCooldown(int cur) {
        if (cur <= 0) return 5;
        if (cur <= 5) return 30;
        if (cur <= 30) return 60;
        return 0;
    }

    private static ItemStack makeNamed(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) im.setLore(lore);
            im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(im);
        }
        return it;
    }

    private static ItemStack makePanel(Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(name);
            it.setItemMeta(im);
        }
        return it;
    }
}
