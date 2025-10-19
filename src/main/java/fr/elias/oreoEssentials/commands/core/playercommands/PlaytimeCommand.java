package fr.elias.oreoEssentials.commands.core.playercommands;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class PlaytimeCommand implements CommandExecutor, TabCompleter {

    private static String fmt(long seconds) {
        long days = TimeUnit.SECONDS.toDays(seconds);
        long hours = TimeUnit.SECONDS.toHours(seconds) % 24;
        long mins = TimeUnit.SECONDS.toMinutes(seconds) % 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0)  sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (mins > 0)  sb.append(mins).append("m ");
        sb.append(secs).append("s");
        return sb.toString().trim();
    }

    private static long vanillaPlaytimeSeconds(Player p) {
        // Vanilla stores PLAY_ONE_MINUTE in ticks; 20 ticks = 1 second
        int ticks = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
        return Math.max(0L, ticks / 20L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // /playtime
        // /playtime <player> (requires oreo.playtime.others)
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Console must specify a player: /" + label + " <player>");
                return true;
            }
            Player self = (Player) sender;
            long secs = vanillaPlaytimeSeconds(self);
            sender.sendMessage("§bYour playtime: §f" + fmt(secs));
            return true;
        }

        // others
        if (!sender.hasPermission("oreo.playtime.others")) {
            sender.sendMessage("§cYou don't have permission.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return true;
        }
        long secs = vanillaPlaytimeSeconds(target);
        sender.sendMessage("§b" + target.getName() + "§7's playtime: §f" + fmt(secs));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("oreo.playtime.others")) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(partial))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
