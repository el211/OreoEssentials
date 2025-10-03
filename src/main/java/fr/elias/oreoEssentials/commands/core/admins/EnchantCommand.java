// src/main/java/fr/elias/oreoEssentials/commands/core/EnchantCommand.java
package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class EnchantCommand implements OreoCommand {
    @Override public String name() { return "enchant"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.enchant"; }
    @Override public String usage() { return "<enchantment> [level] [unsafe] [ignoreConflicts]"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;
        if (args.length < 1) return false;

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            p.sendMessage(ChatColor.RED + "Hold the item you want to enchant.");
            return true;
        }

        Enchantment ench = resolve(args[0]);
        if (ench == null) {
            p.sendMessage(ChatColor.RED + "Unknown enchantment. Try tab-complete.");
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
            p.sendMessage(ChatColor.RED + "Max level for " + enchKey(ench) + " is " + max + ". Use 'unsafe' or permission oreo.enchant.unsafe.");
            return true;
        }

        // conflict check
        if (!ignore && !sender.hasPermission("oreo.enchant.ignoreconflicts")) {
            for (Enchantment ex : item.getEnchantments().keySet()) {
                if (ex.conflictsWith(ench)) {
                    p.sendMessage(ChatColor.RED + "Conflicts with " + enchKey(ex) + ". Add 'ignoreConflicts' or get permission oreo.enchant.ignoreconflicts.");
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
            p.sendMessage(ChatColor.GREEN + "Applied " + ChatColor.AQUA + enchKey(ench) + " " + level);
        } catch (Exception ex) {
            p.sendMessage(ChatColor.RED + "Could not apply enchantment: " + ex.getMessage());
        }
        return true;
    }

    private static String enchKey(Enchantment e) { return e.getKey().toString(); }

    private static Enchantment resolve(String input) {
        String s = input.toLowerCase(Locale.ROOT).trim();
        // try namespaced first (minecraft:sharpness)
        try {
            NamespacedKey key = NamespacedKey.fromString(s);
            if (key != null) {
                Enchantment byKey = Enchantment.getByKey(key);
                if (byKey != null) return byKey;
            }
        } catch (Exception ignored) {}

        // try vanilla key without namespace (sharpness)
        Enchantment byMc = Enchantment.getByKey(NamespacedKey.minecraft(s));
        if (byMc != null) return byMc;

        // fallback: match by simple key string or legacy name
        for (Enchantment e : Enchantment.values()) {
            String simple = e.getKey().getKey(); // e.g. "sharpness"
            if (simple.equalsIgnoreCase(s) || e.getKey().toString().equalsIgnoreCase(s) || e.getName().equalsIgnoreCase(s)) {
                return e;
            }
        }
        return null;
    }
}
