package fr.elias.oreoEssentials.commands.core;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.MuteService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.*;
import java.util.stream.Collectors;

public class MuteCommand implements OreoCommand, org.bukkit.command.TabCompleter {

    private final MuteService mutes;

    public MuteCommand(MuteService mutes) {
        this.mutes = mutes;
    }

    @Override public String name() { return "mute"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.mute"; }
    @Override public String usage() { return "<player> <duration> [reason]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <player> <duration> [reason]");
            sender.sendMessage(ChatColor.GRAY + "Duration examples: 30s, 10m, 2h, 1d");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }

        long durMs = MuteService.parseDurationToMillis(args[1]);
        if (durMs <= 0) {
            sender.sendMessage(ChatColor.RED + "Invalid duration. Use like 30s, 10m, 2h, 1d or seconds.");
            return true;
        }

        String reason = "";
        if (args.length > 2) {
            reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        }

        long until = System.currentTimeMillis() + durMs;
        mutes.mute(target.getUniqueId(), until, reason, sender.getName());

        sender.sendMessage(ChatColor.GREEN + "Muted " + ChatColor.AQUA + target.getName()
                + ChatColor.GREEN + " for " + ChatColor.YELLOW + MuteService.friendlyRemaining(durMs)
                + (reason.isEmpty() ? "" : ChatColor.GREEN + " (Reason: " + ChatColor.YELLOW + reason + ChatColor.GREEN + ")"));

        var p = target.getPlayer();
        if (p != null && p.isOnline()) {
            p.sendMessage(ChatColor.RED + "You have been muted for "
                    + ChatColor.YELLOW + MuteService.friendlyRemaining(durMs)
                    + (reason.isEmpty() ? "" : ChatColor.RED + " | Reason: " + ChatColor.YELLOW + reason));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!sender.hasPermission(permission())) return Collections.emptyList();
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(p -> p.getName())
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return List.of("30s","1m","5m","10m","30m","1h","2h","1d")
                    .stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return Collections.emptyList();
    }
}
