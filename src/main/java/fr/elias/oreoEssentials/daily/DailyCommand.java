package fr.elias.oreoEssentials.daily;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.minuskube.inv.SmartInventory;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Player Commands:
 *  /daily            -> Opens the reward claim GUI
 *  /daily claim      -> Opens the reward claim GUI
 *  /daily top        -> Presents the Reward Streak Leaderboard
 *
 * Permission: oreo.daily (default: true in plugin.yml)
 */
public final class DailyCommand implements CommandExecutor, TabCompleter {

    private final OreoEssentials plugin;
    private final DailyConfig cfg;
    private final DailyService svc;
    private final RewardsConfig rewards;

    public DailyCommand(OreoEssentials plugin, DailyConfig cfg, DailyService svc, RewardsConfig rewards) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.svc = svc;
        this.rewards = rewards;
    }

    private void open(Player p) {
        SmartInventory inv = new DailyMenu(plugin, cfg, svc, rewards).inventory(p);
        inv.open(p);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // /daily top can run from console; GUI only for players
        if (args.length > 0 && args[0].equalsIgnoreCase("top")) {
            showTop(sender);
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage("This command must be run in-game.");
            return true;
        }

        if (!p.hasPermission("oreo.daily")) {
            p.sendMessage(svc.color("&cYou don't have permission to use this."));
            return true;
        }

        // /daily OR /daily claim -> open GUI
        if (args.length == 0 || args[0].equalsIgnoreCase("claim")) {
            open(p);
            return true;
        }

        // Unknown subcommand -> usage
        p.sendMessage(svc.color("&7Usage: &f/" + label + " &7or &f/" + label + " claim &7or &f/" + label + " top"));
        return true;
    }

    private void showTop(CommandSender viewer) {
        record Row(String name, int streak, double hours) {}
        List<Row> rows = new ArrayList<>();

        // Online players: include rough hours from vanilla stat (not persisted)
        for (Player op : Bukkit.getOnlinePlayers()) {
            double hrs = Math.max(0, op.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20.0 / 3600.0);
            rows.add(new Row(op.getName(), svc.getStreak(op.getUniqueId()), hrs));
        }

        // Offline players: streak from Mongo, hours unavailable (0.0)
        for (OfflinePlayer off : Bukkit.getOfflinePlayers()) {
            UUID id = off.getUniqueId();
            if (id == null) continue;

            String name = off.getName() != null ? off.getName() : id.toString();

            // Skip if already added from online set
            boolean dup = rows.stream().anyMatch(r -> r.name.equalsIgnoreCase(name));
            if (dup) continue;

            rows.add(new Row(name, svc.getStreak(id), 0.0));
        }

        // Sort: streak desc, then hours desc
        rows.sort(Comparator.comparingInt(Row::streak).reversed()
                .thenComparingDouble(Row::hours).reversed());

        // Render top 10
        viewer.sendMessage(svc.color("&8&m-------------------------"));
        viewer.sendMessage(svc.color("&b&lDaily &fTop Streaks"));
        int i = 1;
        List<Row> top = rows.stream().limit(10).collect(Collectors.toList());
        for (Row r : top) {
            viewer.sendMessage(svc.color("&7#&f" + (i++) + " &b" + r.name() +
                    " &8» &aStreak: &f" + r.streak() +
                    " &8(&7" + String.format(java.util.Locale.US, "%.1f", r.hours()) + "h&8)"));
        }
        if (top.isEmpty()) viewer.sendMessage(svc.color("&7No data yet."));
        viewer.sendMessage(svc.color("&8&m-------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return Arrays.asList("claim", "top").stream()
                    .filter(s -> s.startsWith(p))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
