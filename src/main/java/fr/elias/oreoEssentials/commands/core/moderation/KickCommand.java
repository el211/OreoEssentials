// File: src/main/java/fr/elias/oreoEssentials/commands/core/KickCommand.java
package fr.elias.oreoEssentials.commands.core.moderation;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.integration.DiscordModerationNotifier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class KickCommand implements OreoCommand {

    @Override public String name() { return "kick"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.kick"; }
    @Override public String usage() { return "<player> [reason...]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <player> [reason...]");
            return true;
        }

        String arg = args[0];
        String reason = args.length >= 2
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : "Kicked by an operator.";

        OreoEssentials plugin = OreoEssentials.get();
        var dir = plugin.getPlayerDirectory();
        var bridge = plugin.getModBridge();

        // 1) LOCAL ONLINE first
        Player local = Bukkit.getPlayerExact(arg);
        if (local != null && local.isOnline()) {
            local.kickPlayer(ChatColor.RED + reason);

            sender.sendMessage(ChatColor.GREEN + "Kicked " + ChatColor.AQUA + local.getName()
                    + ChatColor.GREEN + ". Reason: " + ChatColor.YELLOW + reason);

            notifyDiscord(local.getName(), local.getUniqueId(), reason, sender.getName());
            return true;
        }

        // 2) NETWORK-wide UUID lookup
        UUID uuid = null;

        try {
            if (dir != null) uuid = dir.lookupUuidByName(arg);
        } catch (Throwable ignored) { }

        // Fallback: try parsing UUID
        if (uuid == null) {
            try { uuid = UUID.fromString(arg); } catch (Throwable ignored) { }
        }

        if (uuid == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }

        // 3) Name lookup
        String targetName = arg;
        try {
            if (dir != null) {
                String n = dir.lookupNameByUuid(uuid);
                if (n != null && !n.isBlank()) targetName = n;
            }
        } catch (Throwable ignored) { }

        // 4) CROSS-SERVER KICK via ModBridge
        if (bridge != null) {
            bridge.kick(uuid, targetName, reason);

            sender.sendMessage(ChatColor.GREEN + "Kick request sent for "
                    + ChatColor.AQUA + targetName + ChatColor.GREEN + " (cross-server).");

            notifyDiscord(targetName, uuid, reason, sender.getName());
            return true;
        }

        // 5) If no bridge, fail safely
        sender.sendMessage(ChatColor.RED + "Target is not on this server and cross-server mod bridge is unavailable.");
        return true;
    }

    private void notifyDiscord(String name, UUID uuid, String reason, String actor) {
        DiscordModerationNotifier mod = OreoEssentials.get().getDiscordMod();
        if (mod != null && mod.isEnabled()) {
            mod.notifyKick(name, uuid, reason, actor);
        }
    }

}
