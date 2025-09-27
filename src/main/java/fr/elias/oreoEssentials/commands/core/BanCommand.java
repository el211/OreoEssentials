// src/main/java/fr/elias/oreoEssentials/commands/core/BanCommand.java
package fr.elias.oreoEssentials.commands.core;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BanCommand implements OreoCommand {
    @Override public String name() { return "ban"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.ban"; }
    @Override public String usage() { return "<player> [duration] [reason...]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) return false;

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetName);
        if (target == null) {
            // try online exact, else look up (may be null on first join)
            Player online = Bukkit.getPlayerExact(targetName);
            if (online != null) target = online;
            else target = Bukkit.getOfflinePlayer(targetName);
        }

        Date expires = null;
        String reason = "Banned by an operator.";
        if (args.length >= 2) {
            // If arg[1] looks like a duration (e.g. 10m, 2h, 3d4h)
            Long ms = parseDurationMillis(args[1]);
            if (ms != null) {
                expires = new Date(System.currentTimeMillis() + ms);
                if (args.length >= 3) reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            } else {
                // no duration → treat remainder as reason
                reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            }
        }

        Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), reason, expires, sender.getName());
        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().kickPlayer(ChatColor.RED + reason + (expires != null ? ChatColor.GRAY + "\nUntil: " + expires : ""));
        }
        sender.sendMessage(ChatColor.GREEN + "Banned " + ChatColor.AQUA + targetName +
                (expires != null ? ChatColor.GREEN + " until " + ChatColor.YELLOW + expires : "") +
                ChatColor.GREEN + ". Reason: " + ChatColor.YELLOW + reason);
        return true;
    }

    private static final Pattern DURATION_TOKEN = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);
    private static Long parseDurationMillis(String s) {
        if (s == null || s.isEmpty()) return null;
        Matcher m = DURATION_TOKEN.matcher(s);
        long total = 0;
        int found = 0;
        while (m.find()) {
            long val = Long.parseLong(m.group(1));
            switch (Character.toLowerCase(m.group(2).charAt(0))) {
                case 's' -> total += Duration.ofSeconds(val).toMillis();
                case 'm' -> total += Duration.ofMinutes(val).toMillis();
                case 'h' -> total += Duration.ofHours(val).toMillis();
                case 'd' -> total += Duration.ofDays(val).toMillis();
                case 'w' -> total += Duration.ofDays(val * 7).toMillis();
            }
            found++;
        }
        return found == 0 ? null : total;
    }
}
