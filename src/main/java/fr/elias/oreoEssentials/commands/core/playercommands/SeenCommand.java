package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SeenCommand implements OreoCommand {
    @Override public String name() { return "seen"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.seen"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    @SuppressWarnings("deprecation")
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /seen <player>");
            return true;
        }

        String name = args[0];
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        if (op == null || (op.getName() == null && !op.hasPlayedBefore())) {
            sender.sendMessage(ChatColor.RED + "No data for that player.");
            return true;
        }

        if (op.isOnline()) {
            sender.sendMessage(ChatColor.GREEN + (op.getName() != null ? op.getName() : name) + " is currently online.");
            return true;
        }

        long last = tryGetLong(op, "getLastSeen");      // Paper method (if present)
        if (last <= 0L) last = tryGetLong(op, "getLastLogin"); // Paper method (if present)
        if (last <= 0L) last = op.getLastPlayed();      // Bukkit/Spigot

        if (last <= 0L) {
            sender.sendMessage(ChatColor.YELLOW + (op.getName() != null ? op.getName() : name) + " has never joined.");
            return true;
        }

        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(last));
        String ago = formatAgo(System.currentTimeMillis() - last);

        sender.sendMessage(
                ChatColor.GOLD + (op.getName() != null ? op.getName() : name) +
                        ChatColor.GRAY + " was last seen at " + ChatColor.AQUA + ts +
                        ChatColor.GRAY + " (" + ChatColor.YELLOW + ago + ChatColor.GRAY + " ago)"
        );
        return true;
    }

    /** Try to call a no-arg long-returning method reflectively (Paper compatibility). */
    private long tryGetLong(OfflinePlayer op, String methodName) {
        try {
            Method m = op.getClass().getMethod(methodName);
            Object v = m.invoke(op);
            if (v instanceof Long l) return l;
            if (v instanceof Number n) return n.longValue();
        } catch (Throwable ignored) {}
        return -1L;
    }

    /** Compact "time ago" formatter. */
    private String formatAgo(long millis) {
        if (millis < 0) millis = 0;
        Duration d = Duration.ofMillis(millis);
        long days = d.toDays();
        long hours = d.minusDays(days).toHours();
        long mins = d.minusDays(days).minusHours(hours).toMinutes();

        if (days > 0) {
            if (hours > 0) return days + "d " + hours + "h";
            return days + "d";
        }
        if (hours > 0) {
            if (mins > 0) return hours + "h " + mins + "m";
            return hours + "h";
        }
        if (mins > 0) return mins + "m";
        long secs = Math.max(0, d.getSeconds());
        return secs + "s";
    }
}
