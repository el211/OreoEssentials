package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.rabbitmq.PacketChannels;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.SendRemoteMessagePacket;
import fr.elias.oreoEssentials.services.TeleportService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class TpaCommand implements OreoCommand {
    private final TeleportService tpa;

    public TpaCommand(TeleportService tpa) { this.tpa = tpa; }

    @Override public String name() { return "tpa"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.tpa"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (args.length < 1) return false;

        final String targetNameRaw = args[0];
        final String targetName = targetNameRaw.trim();
        if (targetName.isEmpty()) { p.sendMessage("§cUsage: /tpa <player>"); return true; }

        // 1) Try LOCAL first
        Player local = Bukkit.getPlayerExact(targetName);
        if (local != null) {
            if (local.equals(p)) { p.sendMessage("§cYou cannot TPA to yourself."); return true; }
            tpa.request(p, local);
            return true;
        }

        // 2) Not local: try cross-server hints via PlayerDirectory
        final var plugin = OreoEssentials.get();
        final var dir = plugin.getPlayerDirectory(); // created in onEnable() when using Mongo
        final String localServer = plugin.getConfigService().serverName();

        // Resolve UUID (best-effort)
        UUID targetUuid = null;
        try {
            OfflinePlayer off = Bukkit.getOfflinePlayer(targetName);
            if (off != null && off.hasPlayedBefore()) {
                targetUuid = off.getUniqueId();
            }
        } catch (Throwable ignored) {}

        if (dir != null && targetUuid != null) {
            // Ask the directory which server the player was last seen on (or currently on)
            String where = null;
            try {
                // Prefer a "current/online" lookup if your PlayerDirectory exposes it;
                // otherwise fall back to "lastServer" semantics.
                where = dir.getCurrentOrLastServer(targetUuid); // <— implement this or alias to your existing method
            } catch (Throwable ignored) {}

            if (where != null && !where.isBlank()) {
                if (!where.equalsIgnoreCase(localServer)) {
                    // 2a) Target is on ANOTHER server — guide the requester and (optionally) ping target via Rabbit
                    p.sendMessage("§e" + targetName + " §7is on server §b" + where + "§7.");
                    p.sendMessage("§7Use §b/server " + where + " §7then run §b/tpa " + targetName + "§7.");

                    // Optional: send a remote chat ping to the target's server if RabbitMQ is online
                    PacketManager pm = plugin.getPacketManager();
                    if (pm != null && pm.isInitialized()) {
                        String msg = "§b" + p.getName() + "§7 requested to teleport to you. Type §a/tpaccept " +
                                p.getName() + " §7or §c/tpdeny " + p.getName() + "§7.";
                        try {
                            pm.sendPacket(
                                    PacketChannels.GLOBAL, // or PacketChannel.individual(where) if you route per-server
                                    new SendRemoteMessagePacket(targetUuid, msg)
                            );
                            p.sendMessage("§7(They were notified on §b" + where + "§7.)");
                        } catch (Throwable t) {
                            // just ignore; hint already shown
                        }
                    }
                    return true;
                }
            }
        }

        // 3) We couldn't find them cross-server (no directory, or no UUID record, or same server but offline)
        p.sendMessage("§cPlayer not found online. §7(They may be offline or on another proxy cluster.)");
        return true;
    }
}
