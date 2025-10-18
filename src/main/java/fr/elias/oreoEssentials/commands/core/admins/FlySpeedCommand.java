package fr.elias.oreoEssentials.commands.core.admins;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class FlySpeedCommand implements TabExecutor {

    private static final List<String> SPEED_SUGGEST =
            IntStream.rangeClosed(1, 10).mapToObj(String::valueOf).collect(Collectors.toList());

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use /flyspeed (sets your own flight speed).");
            return true;
        }
        if (!sender.hasPermission("oreo.flyspeed")) {
            sender.sendMessage("§cYou don't have permission (oreo.flyspeed).");
            return true;
        }

        Player p = (Player) sender;

        if (args.length == 0) {
            p.sendMessage("§eUsage: §f/flyspeed <1-10|reset>");
            p.sendMessage("§7Example: §f/flyspeed 7");
            return true;
        }

        if (args[0].equalsIgnoreCase("reset")) {
            trySetSpeed(p, 0.1f);
            p.sendMessage("§aFly speed reset to §f0.1 §7(= level 1).");
            return true;
        }

        Integer level = parseInt(args[0]);
        if (level == null || level < 1 || level > 10) {
            p.sendMessage("§cInvalid speed. Use a number §f1-10 §cor §freset§c.");
            return true;
        }

        float speed = level / 10.0f; // 1→0.1f, 10→1.0f
        trySetSpeed(p, speed);
        p.sendMessage("§aFly speed set to §f" + speed + " §7(§flevel " + level + "§7)."
                + (p.getAllowFlight() ? "" : " §8(You’re not allowed to fly right now.)"));
        return true;
    }

    private static Integer parseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private static void trySetSpeed(Player p, float speed) {
        // Bukkit expects -1.0..1.0; we use 0.1..1.0
        try { p.setFlySpeed(speed); }
        catch (IllegalArgumentException ex) { p.setFlySpeed(Math.max(-1f, Math.min(1f, speed))); }
    }

    // ---- Tab complete ----
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("oreo.flyspeed")) return Collections.emptyList();

        if (args.length == 1) {
            List<String> base = new ArrayList<>();
            base.add("reset");
            base.addAll(SPEED_SUGGEST);
            String pref = args[0].toLowerCase(Locale.ROOT);
            return base.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(pref)).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
