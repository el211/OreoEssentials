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

public class DisenchantCommand implements OreoCommand {

    @Override public String name() { return "disenchant"; }
    @Override public List<String> aliases() { return List.of("unenchant", "deench"); }
    @Override public String permission() { return "oreo.disenchant"; }
    @Override public String usage() { return "<enchantment|all> [levels-to-remove]"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;

        if (args.length < 1) {
            Lang.send(p, "admin.disenchant.usage", Map.of("label", label), p);
            return true;
        }

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            Lang.send(p, "admin.enchant.hold-item", null, p);
            return true;
        }

        if (item.getEnchantments().isEmpty()) {
            Lang.send(p, "admin.disenchant.none", null, p);
            return true;
        }

        String target = args[0].toLowerCase(Locale.ROOT).trim();

        // Remove ALL enchantments
        if (target.equals("all") || target.equals("*")) {
            item.getEnchantments().keySet().forEach(item::removeEnchantment);
            Lang.send(p, "admin.disenchant.all", null, p);
            return true;
        }

        Enchantment ench = resolve(target);
        if (ench == null) {
            Lang.send(p, "admin.enchant.unknown", Map.of("input", args[0]), p);
            return true;
        }

        if (!item.getEnchantments().containsKey(ench)) {
            Lang.send(p, "admin.disenchant.not-present",
                    Map.of("ench", enchKey(ench)), p);
            return true;
        }

        int current = item.getEnchantments().get(ench);
        int remove = 1;

        if (args.length >= 2) {
            try { remove = Math.max(1, Integer.parseInt(args[1])); }
            catch (NumberFormatException ignored) {}
        }

        int newLevel = current - remove;

        if (newLevel > 0) {
            item.addUnsafeEnchantment(ench, newLevel);
            Lang.send(p, "admin.disenchant.partial",
                    Map.of("ench", enchKey(ench),
                            "removed", String.valueOf(remove),
                            "remaining", String.valueOf(newLevel)), p);
        } else {
            item.removeEnchantment(ench);
            Lang.send(p, "admin.disenchant.removed",
                    Map.of("ench", enchKey(ench)), p);
        }

        return true;
    }

    private static String enchKey(Enchantment e) {
        return e.getKey().toString();
    }

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
