// File: src/main/java/fr/elias/oreoEssentials/commands/core/UnmuteCommand.java
package fr.elias.oreoEssentials.commands.core.moderation;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.integration.DiscordModerationNotifier;
import fr.elias.oreoEssentials.services.MuteService;
import fr.elias.oreoEssentials.util.ChatSyncManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.*;
import java.util.stream.Collectors;

public class UnmuteCommand implements OreoCommand, org.bukkit.command.TabCompleter {

    private final MuteService mutes;
    private final ChatSyncManager chatSync; // may be null if sync disabled

    public UnmuteCommand(MuteService mutes, ChatSyncManager chatSync) {
        this.mutes = mutes;
        this.chatSync = chatSync;
    }

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
        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }

        boolean wasMuted = mutes.unmute(target.getUniqueId());
        if (!wasMuted) {
            sender.sendMessage(ChatColor.GRAY + target.getName() + " is not muted.");
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + "Unmuted " + ChatColor.AQUA + target.getName() + ChatColor.GREEN + ".");

        // Tell other servers
        try {
            if (chatSync != null) {
                chatSync.broadcastUnmute(target.getUniqueId());
            }
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[OreoEssentials] Failed to broadcast UNMUTE: " + t.getMessage());
        }

        // Discord notification
        DiscordModerationNotifier mod = OreoEssentials.get().getDiscordMod();
        if (mod != null && mod.isEnabled()) {
            mod.notifyUnmute(target.getName(), target.getUniqueId(), sender.getName());
        }

        // Notify player if online
        var p = target.getPlayer();
        if (p != null && p.isOnline()) {
            p.sendMessage(ChatColor.GREEN + "You have been unmuted.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!sender.hasPermission(permission())) return Collections.emptyList();
        if (args.length == 1) {
            // Suggest currently-muted players by name (best-effort)
            return mutes.allMuted().stream()
                    .map(uuid -> {
                        var op = Bukkit.getOfflinePlayer(uuid);
                        return op != null && op.getName() != null ? op.getName() : uuid.toString();
                    })
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
