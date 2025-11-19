// File: src/main/java/fr/elias/oreoEssentials/commands/core/moderation/MuteCommand.java
package fr.elias.oreoEssentials.commands.core.moderation;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.integration.DiscordModerationNotifier;
import fr.elias.oreoEssentials.playerdirectory.PlayerDirectory;
import fr.elias.oreoEssentials.services.chatservices.MuteService;
import fr.elias.oreoEssentials.util.ChatSyncManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.*;
import java.util.stream.Collectors;

public class MuteCommand implements OreoCommand, org.bukkit.command.TabCompleter {

    private final MuteService mutes;
    private final ChatSyncManager sync; // may be null if cross-chat disabled

    public MuteCommand(MuteService mutes, ChatSyncManager sync) {
        this.mutes = mutes;
        this.sync = sync;
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

        String targetArg = args[0];

        // First: try normal Bukkit offline lookup
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetArg);

        // If Bukkit has no record, try PlayerDirectory to resolve UUID by name
        if ((target == null || (target.getName() == null && !target.hasPlayedBefore()))) {
            PlayerDirectory dir = OreoEssentials.get().getPlayerDirectory();
            if (dir != null) {
                try {
                    UUID id = dir.lookupUuidByName(targetArg);
                    if (id != null) {
                        target = Bukkit.getOfflinePlayer(id);
                    }
                } catch (Throwable ignored) {}
            }
        }

        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + targetArg);
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

        long untilEpochMillis = System.currentTimeMillis() + durMs;

        // Apply locally
        mutes.mute(target.getUniqueId(), untilEpochMillis, reason, sender.getName());

        // Discord notification
        DiscordModerationNotifier mod = OreoEssentials.get().getDiscordMod();
        if (mod != null && mod.isEnabled()) {
            mod.notifyMute(target.getName(), target.getUniqueId(), reason, sender.getName(), untilEpochMillis);
        }

        // Broadcast to other servers (so they also store the mute)
        try {
            if (sync != null) {
                sync.broadcastMute(target.getUniqueId(), untilEpochMillis, reason, sender.getName());
            }
        } catch (Throwable t) {
            sender.sendMessage(ChatColor.RED + "Warning: failed to broadcast mute to other servers.");
        }

        // Feedback
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
    public List<String> onTabComplete(org.bukkit.command.CommandSender sender,
                                      org.bukkit.command.Command command,
                                      String label,
                                      String[] args) {
        if (!sender.hasPermission(permission())) return Collections.emptyList();

        // Arg 1: player name (local + cross-server)
        if (args.length == 1) {
            final String want = args[0].toLowerCase(Locale.ROOT);
            Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

            // 1) Local online players
            Bukkit.getOnlinePlayers().forEach(p -> {
                String n = p.getName();
                if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) {
                    out.add(n);
                }
            });

            // 2) Network-wide via PlayerDirectory
            PlayerDirectory dir = OreoEssentials.get().getPlayerDirectory();
            if (dir != null) {
                try {
                    Collection<String> names = dir.suggestOnlineNames(want, 50);
                    if (names != null) {
                        for (String n : names) {
                            if (n != null && n.toLowerCase(Locale.ROOT).startsWith(want)) {
                                out.add(n);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            return out.stream().limit(50).collect(Collectors.toList());
        }

        // Arg 2: duration presets
        if (args.length == 2) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            return List.of("30s", "1m", "5m", "10m", "30m", "1h", "2h", "1d").stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(partial))
                    .toList();
        }

        return Collections.emptyList();
    }
}
