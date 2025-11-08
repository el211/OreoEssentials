package fr.elias.oreoEssentials.trade;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TradeCommand implements CommandExecutor {

    private final OreoEssentials plugin;
    private final TradeService service;

    public TradeCommand(OreoEssentials plugin, TradeService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        // guard if disabled mid-runtime
        if (OreoEssentials.get().getTradeService() == null) {
            p.sendMessage("§cTrading is currently disabled.");
            return true;
        }
        if (!p.hasPermission("oreo.trade")) {
            p.sendMessage("§cYou don't have permission.");
            return true;
        }
        if (args.length != 1) {
            p.sendMessage("§eUsage: /trade <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            p.sendMessage("§cPlayer not found.");
            return true;
        }
        if (target.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage("§cYou cannot trade with yourself.");
            return true;
        }
        service.startTrade(p, target);
        return true;
    }
}
