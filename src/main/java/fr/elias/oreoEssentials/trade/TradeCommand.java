// File: src/main/java/fr/elias/oreoEssentials/trade/TradeCommand.java
package fr.elias.oreoEssentials.trade;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class TradeCommand implements CommandExecutor {

    private final OreoEssentials plugin;
    private final TradeService service;

    public TradeCommand(OreoEssentials plugin, TradeService service) {
        this.plugin = plugin;
        this.service = service;
    }

    /* ----------------------------- Debug helpers ----------------------------- */
    private boolean dbg() {
        try {
            return service != null && service.getConfig() != null && service.getConfig().debugDeep;
        } catch (Throwable t) {
            return false;
        }
    }
    private void log(String msg) { if (dbg()) plugin.getLogger().info(msg); }

    /* ----------------------------- Command ----------------------------- */
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (plugin.getTradeService() == null) {
            p.sendMessage("§cTrading is currently disabled.");
            log("[TRADE] /trade denied: service null");
            return true;
        }

        if (!p.hasPermission("oreo.trade")) {
            p.sendMessage("§cYou don't have permission.");
            log("[TRADE] /trade denied: no permission for " + p.getName());
            return true;
        }

        if (args.length != 1) {
            p.sendMessage("§eUsage: /trade <player>");
            log("[TRADE] /trade bad usage by " + p.getName());
            return true;
        }

        final String targetName = args[0];
        log("[TRADE] /trade invoked by=" + p.getName() + " arg=" + targetName);

        // 1) If this player has an invite FROM <targetName>, accept it.
        boolean accepted = service.tryAcceptInvite(p, targetName);
        if (!accepted && service.tryAcceptInviteAny(p)) return true;

        log("[TRADE] tryAcceptInvite result=" + accepted + " acceptor=" + p.getName() + " requesterName=" + targetName);
        if (accepted) return true;

        // 2) Otherwise this is a NEW invite.
        Player localTarget = Bukkit.getPlayerExact(targetName);
        log("[TRADE] resolve local target -> " + (localTarget != null ? ("FOUND uuid=" + localTarget.getUniqueId()) : "not found"));

        // Local self-check
        if (localTarget != null && localTarget.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage("§cYou cannot trade with yourself.");
            log("[TRADE] /trade self-invite blocked for " + p.getName());
            return true;
        }

        // If local target exists and is online → local invite
        if (localTarget != null && localTarget.isOnline()) {
            log("[TRADE] sending LOCAL invite: " + p.getName() + " -> " + localTarget.getName());
            service.sendInvite(p, localTarget); // just sends an invite; GUI opens on accept
            return true;
        }

        // 3) Cross-server path (remote)
        var broker = plugin.getTradeBroker();
        boolean msgAvail = plugin.isMessagingAvailable();
        log("[TRADE] cross-server path: broker=" + (broker != null) + " messagingAvailable=" + msgAvail);

        if (broker != null && msgAvail) {
            // resolve UUID using PlayerDirectory
            UUID targetId = null;
            try {
                var dir = plugin.getPlayerDirectory();
                if (dir != null) {
                    targetId = dir.lookupUuidByName(targetName);
                    log("[TRADE] PlayerDirectory lookup name=" + targetName + " -> " + targetId);
                } else {
                    log("[TRADE] PlayerDirectory is null; cannot resolve remote UUID.");
                }
            } catch (Throwable t) {
                log("[TRADE] PlayerDirectory lookup error: " + t.getMessage());
            }

            if (targetId != null && !targetId.equals(p.getUniqueId())) {
                log("[TRADE] sending REMOTE invite: " + p.getName() + " -> " + targetName + " (" + targetId + ")");
                broker.sendInvite(p, targetId, targetName);  // no GUI yet; receiver gets an invite
                return true;
            } else {
                log("[TRADE] remote invite aborted: targetId=" + targetId + " (self? " + p.getUniqueId().equals(targetId) + ")");
            }
        } else {
            log("[TRADE] remote invite unavailable: broker or messaging missing.");
        }

        p.sendMessage("§cPlayer not found here. §7If this is a cross-server trade, type §b/trade " + targetName + " §7on the server where you received the invite.");
        return true;
    }
}
