package fr.elias.oreoEssentials.customcraft;

import fr.elias.oreoEssentials.util.Lang;
import fr.minuskube.inv.InventoryManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class OeCraftCommand implements CommandExecutor, TabCompleter {
    private final Plugin plugin;
    private final InventoryManager invMgr;
    private final CustomCraftingService service;

    public OeCraftCommand(Plugin plugin, InventoryManager invMgr, CustomCraftingService service) {
        this.plugin = plugin;
        this.invMgr = invMgr;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // ---- Help / no args ----
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendUsage(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // ---- Reload (console OK) ----
        if (sub.equals("reload")) {
            if (!sender.hasPermission("oreo.craft")) {
                sender.sendMessage(t("economy.errors.no-permission"));
                return true;
            }
            service.loadAllAndRegister();
            sender.sendMessage(t("customcraft.messages.reloaded"));
            return true;
        }

        // ---- List (console OK) ----
        if (sub.equals("list")) {
            var names = new TreeSet<>(service.allNames());
            sender.sendMessage(tp("customcraft.messages.list",
                    Map.of("count", String.valueOf(names.size()),
                            "names", String.join(", ", names))));
            return true;
        }

        // ---- Delete (console OK) ----
        if (sub.equals("delete")) {
            if (!sender.hasPermission("oreo.craft")) {
                sender.sendMessage(t("economy.errors.no-permission"));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /" + label + " delete <name>");
                return true;
            }
            String name = sanitize(args[1]);
            boolean ok = service.delete(name);
            sender.sendMessage(ok
                    ? tp("customcraft.messages.deleted", Map.of("name", name))
                    : t("customcraft.messages.invalid"));
            return true;
        }

        // ---- Everything below requires a Player (GUI) ----
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cThis subcommand requires a player.");
            return true;
        }
        if (!p.hasPermission("oreo.craft")) {
            p.sendMessage(t("economy.errors.no-permission"));
            return true;
        }

        // ---- Browse (GUI) ----
        if (sub.equals("browse")) {
            RecipeListMenu.open(p, plugin, invMgr, service);
            return true;
        }

        // ---- Craft <name> (open editor / create-or-edit) ----
        if (sub.equals("craft")) {
            if (args.length < 2) {
                p.sendMessage("§cUsage: /" + label + " craft <name>");
                return true;
            }
            String name = sanitize(args[1]);
            CraftDesignerMenu.build(plugin, invMgr, service, name).open(p);
            return true;
        }

        p.sendMessage("§cUnknown subcommand. Use /" + label + " help");
        return true;
    }

    private static String sanitize(String raw) {
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage("§eUsage:");
        sender.sendMessage("§e/" + label + " browse §7→ open recipe browser (GUI)");
        sender.sendMessage("§e/" + label + " craft <name> §7→ open designer GUI for <name>");
        sender.sendMessage("§e/" + label + " list §7→ list recipe names");
        sender.sendMessage("§e/" + label + " reload §7→ reload recipes.yml & re-register");
        sender.sendMessage("§e/" + label + " delete <name> §7→ delete a recipe");
    }

    /* ---------------- Tab Completion ---------------- */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            List<String> base = List.of("browse", "craft", "list", "reload", "delete");
            String pref = args[0].toLowerCase(Locale.ROOT);
            out = base.stream().filter(s -> s.startsWith(pref)).collect(Collectors.toList());
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("craft") || sub.equals("delete")) {
                String pref = args[1].toLowerCase(Locale.ROOT);
                out = service.allNames().stream()
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(pref))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
            }
        }

        return out;
    }

    /* ---------------- Lang helpers (correct signatures) ---------------- */

    /* ---------------- Lang helpers (key, default) ---------------- */

    private static String t(String path) {
        String s = Lang.get(path, path); // fall back to the key itself
        return (s == null) ? path : s;
    }

    private static String tp(String path, Map<String, String> ph) {
        String s = t(path);
        if (ph == null || ph.isEmpty()) return s;
        for (var e : ph.entrySet()) {
            s = s.replace("%" + e.getKey() + "%", e.getValue());
        }
        return s;
    }

}
