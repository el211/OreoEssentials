package fr.elias.oreoEssentials.commands.core;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.MuteService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class UnmuteCommand implements OreoCommand, org.bukkit.command.TabCompleter {

    private final MuteService mutes;

    public UnmuteCommand(MuteService mutes) { this.mutes = mutes; }

    @Override public String name() { return "unmute"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.unmute"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <player>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }
        boolean ok = mutes.unmute(target.getUniqueId());
        sender.sendMessage(ok
                ? ChatColor.GREEN + "Unmuted " + ChatColor.AQUA + target.getName()
                : ChatColor.RED + "That player is not muted.");
        if (target.getPlayer() != null && target.getPlayer().isOnline() && ok) {
            target.getPlayer().sendMessage(ChatColor.GREEN + "You have been unmuted.");
        }
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (args.length == 1) {
            // suggest currently-muted names
            return mutes.allMuted().stream()
                    .map(uuid -> {
                        var op = Bukkit.getOfflinePlayer(uuid);
                        return op != null && op.getName() != null ? op.getName() : uuid.toString();
                    })
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        return java.util.Collections.emptyList();
    }
}
