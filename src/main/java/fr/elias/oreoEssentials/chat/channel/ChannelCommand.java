package fr.elias.oreoEssentials.chat.channel;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class ChannelCommand implements CommandExecutor, TabCompleter {
    private final ChannelManager channels;
    private final ChannelsConfig cfg;

    public ChannelCommand(ChannelManager channels, ChannelsConfig cfg) {
        this.channels = channels;
        this.cfg = cfg;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String pfx = ChatColor.translateAlternateColorCodes('&', cfg.getPrefix());
        if (!(sender instanceof Player p)) {
            sender.sendMessage(pfx + ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(pfx + ChatColor.YELLOW + "Usage: /" + label + " <list|join|leave|speak> [channel]");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                var joined = String.join(", ", new TreeSet<>(channels.recipientsFor(channels.getSpeakChannel(p) == null ? "" : channels.getSpeakChannel(p))
                        .stream().map(Player::getName).toList())); // little easter: show who shares your speak channel
                var all = channels.all().stream().map(ChatChannel::getName).sorted().collect(Collectors.joining(", "));
                p.sendMessage(pfx + ChatColor.AQUA + "Channels: " + ChatColor.GRAY + all);
                String speak = channels.getSpeakChannel(p);
                p.sendMessage(pfx + ChatColor.AQUA + "You are speaking in: " + ChatColor.YELLOW + (speak == null ? "none" : speak));
                p.sendMessage(pfx + ChatColor.AQUA + "Players in your speak channel: " + ChatColor.GRAY + (joined.isEmpty() ? "-" : joined));
            }
            case "join" -> {
                if (args.length < 2) { p.sendMessage(pfx + ChatColor.YELLOW + "Usage: /" + label + " join <channel>"); return true; }
                String ch = args[1];
                if (!channels.join(p, ch)) {
                    p.sendMessage(pfx + ChatColor.RED + "Cannot join that channel (no permission or not found).");
                    return true;
                }
                p.sendMessage(pfx + ChatColor.GREEN + "Joined channel " + ChatColor.AQUA + ch.toLowerCase(Locale.ROOT) + ChatColor.GREEN + ".");
            }
            case "leave" -> {
                if (args.length < 2) { p.sendMessage(pfx + ChatColor.YELLOW + "Usage: /" + label + " leave <channel>"); return true; }
                String ch = args[1];
                if (!channels.leave(p, ch)) {
                    p.sendMessage(pfx + ChatColor.RED + "Cannot leave that channel (no permission, not joined, or not found).");
                    return true;
                }
                p.sendMessage(pfx + ChatColor.GREEN + "Left channel " + ChatColor.AQUA + ch.toLowerCase(Locale.ROOT) + ChatColor.GREEN + ".");
            }
            case "speak" -> {
                if (args.length < 2) { p.sendMessage(pfx + ChatColor.YELLOW + "Usage: /" + label + " speak <channel>"); return true; }
                String ch = args[1];
                if (!channels.setSpeak(p, ch)) {
                    p.sendMessage(pfx + ChatColor.RED + "Cannot speak in that channel (not joined, read-only, or no permission).");
                    return true;
                }
                p.sendMessage(pfx + ChatColor.GREEN + "You now speak in " + ChatColor.AQUA + ch.toLowerCase(Locale.ROOT) + ChatColor.GREEN + ".");
            }
            default -> p.sendMessage(pfx + ChatColor.YELLOW + "Usage: /" + label + " <list|join|leave|speak> [channel]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player p)) return Collections.emptyList();
        if (args.length == 1) return List.of("list", "join", "leave", "speak")
                .stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();

        if (args.length == 2 && (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("leave") || args[0].equalsIgnoreCase("speak"))) {
            return channels.all().stream().map(ChatChannel::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .sorted().toList();
        }
        return Collections.emptyList();
    }
}
