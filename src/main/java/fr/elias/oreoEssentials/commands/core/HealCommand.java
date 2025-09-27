// File: src/main/java/fr/elias/oreoEssentials/commands/core/HealCommand.java
package fr.elias.oreoEssentials.commands.core;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;

public class HealCommand implements OreoCommand {
    @Override public String name() { return "heal"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.heal"; }
    @Override public String usage() { return "[player]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage("§cPlayer not found."); return true; }
        } else {
            if (!(sender instanceof Player)) return false;
            target = (Player) sender;
        }

        double max = resolveMaxHealth(target);
        target.setHealth(max);          // why: set to allowed cap only
        target.setFireTicks(0);
        target.sendMessage("§aHealed.");
        if (target != sender) sender.sendMessage("§eHealed §b" + target.getName());
        return true;
    }

    private static double resolveMaxHealth(Player p) {
        // Try new name first
        Double val = tryAttribute(p, "GENERIC_MAX_HEALTH");
        if (val != null) return val;

        // Try older enum name on some API baselines
        val = tryAttribute(p, "MAX_HEALTH");
        if (val != null) return val;

        // Last-resort: legacy method via reflection (older Bukkit)
        try {
            Method m = p.getClass().getMethod("getMaxHealth");
            Object r = m.invoke(p);
            if (r instanceof Number n) return n.doubleValue();
        } catch (Exception ignored) {}

        return 20.0; // sensible default
    }

    private static Double tryAttribute(Player p, String attrName) {
        try {
            Attribute a = Attribute.valueOf(attrName);
            AttributeInstance inst = p.getAttribute(a);
            if (inst != null) return inst.getValue();
        } catch (IllegalArgumentException ignored) {
            // why: enum constant not present on this API version
        }
        return null;
    }
}
