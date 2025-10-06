package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EnchantCommand implements OreoCommand {
    @Override public String name() { return "enchant"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.enchant"; }
    @Override public String usage() { return "<enchantment> [level] [unsafe] [ignoreConflicts]"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;
        if (args.length < 1) {
            Lang.send(p, "admin.enchant.usage", Map.of("label", label), p);
            return true;
        }

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            Lang.send(p, "admin.enchant.hold-item", null, p);
            return true;
        }

        Enchantment ench = resolve(args[0]);
        if (ench == null) {
            Lang.send(p, "admin.enchant.unknown", Map.of("input", args[0]), p);
            return true;
        }

        int level = 1;
        if (args.length >= 2) {
            try { level = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
        }

        boolean unsafe = args.length >= 3 && args[2].equalsIgnoreCase("unsafe");
        boolean ignore = args.length >= 4 && args[3].equalsIgnoreCase("ignoreConflicts");

        int max = ench.getMaxLevel();
        if (level < 1) level = 1;
        if (level > max && !(unsafe || sender.hasPermission("oreo.enchant.unsafe"))) {
            Lang.send(p, "admin.enchant.too-high",
                    Map.of("ench", enchKey(ench), "max", String.valueOf(max)), p);
            return true;
        }

        if (!ignore && !sender.hasPermission("oreo.enchant.ignoreconflicts")) {
            for (Enchantment ex : item.getEnchantments().keySet()) {
                if (ex.conflictsWith(ench)) {
                    Lang.send(p, "admin.enchant.conflict",
                            Map.of("with", enchKey(ex)), p);
                    return true;
                }
            }
        }

        try {
            if (unsafe || sender.hasPermission("oreo.enchant.unsafe")) {
                item.addUnsafeEnchantment(ench, level);
            } else {
                item.addEnchantment(ench, Math.min(level, max));
            }
            Lang.send(p, "admin.enchant.applied",
                    Map.of("ench", enchKey(ench), "level", String.valueOf(level)), p);
        } catch (Exception ex) {
            Lang.send(p, "admin.enchant.failed",
                    Map.of("reason", ex.getMessage() == null ? "unknown" : ex.getMessage()), p);
        }
        return true;
    }

    private static String enchKey(Enchantment e) { return e.getKey().toString(); }

    private static Enchantment resolve(String input) {
        String s = input.toLowerCase(Locale.ROOT).trim();
        try {
            NamespacedKey key = NamespacedKey.fromString(s);
            if (key != null) {
                Enchantment byKey = Enchantment.getByKey(key);
                if (byKey != null) return byKey;
            }
        } catch (Exception ignored) {}

        Enchantment byMc = Enchantment.getByKey(NamespacedKey.minecraft(s));
        if (byMc != null) return byMc;

        for (Enchantment e : Enchantment.values()) {
            String simple = e.getKey().getKey();
            if (simple.equalsIgnoreCase(s)
                    || e.getKey().toString().equalsIgnoreCase(s)
                    || (e.getName() != null && e.getName().equalsIgnoreCase(s))) {
                return e;
            }
        }
        return null;
    }
}
