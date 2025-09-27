package fr.elias.oreoEssentials.commands.ecocommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Async;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

public class MoneyCommand implements CommandExecutor {

    private final OreoEssentials plugin;
    private final Economy vault; // may be null if Vault/provider missing

    public MoneyCommand(OreoEssentials plugin) {
        this.plugin = plugin;
        Economy found = null;
        try {
            var rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) found = rsp.getProvider();
        } catch (Throwable ignored) {}
        this.vault = found; // can be null; guarded where used
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // /money
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players can check their own balance.");
                return true;
            }
            showSelfBalance(player);
            return true;
        }

        // /money <player> (view other's balance)
        if (args.length == 1) {
            String targetName = args[0];
            showOtherBalance(sender, targetName);
            return true;
        }

        // /money <give|take|set> <player> <amount>
        if (args.length >= 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String targetName = args[1];
            double amount;

            if (!sub.equals("give") && !sub.equals("take") && !sub.equals("set")) {
                sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <give|take|set> <player> <amount>");
                return true;
            }

            if (!sender.hasPermission("OreoEssentials.money." + sub)) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                return true;
            }

            try {
                amount = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid amount.");
                return true;
            }
            if (amount <= 0 && !sub.equals("set")) {
                sender.sendMessage(ChatColor.RED + "Amount must be greater than zero.");
                return true;
            }

            if (plugin.getDatabase() == null) {
                sender.sendMessage(ChatColor.RED + "Economy database is not configured.");
                return true;
            }

            final double amt = amount;
            Async.run(() -> {
                UUID targetId = plugin.getOfflinePlayerCache().getId(targetName);
                if (targetId == null) {
                    sendSync(sender, ChatColor.RED + "Player not found.");
                    return;
                }

                switch (sub) {
                    case "give" -> plugin.getDatabase().giveBalance(targetId, targetName, amt);
                    case "take" -> plugin.getDatabase().takeBalance(targetId, targetName, amt);
                    case "set"  -> plugin.getDatabase().setBalance(targetId, targetName, amt);
                }

                // optional cross-server broadcast
                var pm = plugin.getPacketManager();
                if (pm != null && pm.isInitialized()) {
                    try {
                        // pm.publish(new BalanceUpdatePacket(targetId, targetName));
                    } catch (Throwable ignored) {}
                }

                sendSync(sender, ChatColor.GREEN + "Transaction applied to " + ChatColor.AQUA + targetName + ChatColor.GREEN + ".");
            });
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Usage: /" + label + " [player] | <give|take|set> <player> <amount>");
        return true;
    }

    /* -------------------------- helpers -------------------------- */

    private void showSelfBalance(Player player) {
        // Prefer DB if present; else fall back to Vault
        if (plugin.getDatabase() != null) {
            Async.run(() -> {
                double bal = plugin.getDatabase().getBalance(player.getUniqueId());
                sendSync(player, ChatColor.GREEN + "Your balance: $" + format(bal));
            });
            return;
        }
        if (vault != null) {
            double bal = safeVaultBalance(player);
            player.sendMessage(ChatColor.GREEN + "Your balance: $" + format(bal));
            return;
        }
        player.sendMessage(ChatColor.RED + "No economy backend available.");
    }

    private void showOtherBalance(CommandSender sender, String targetName) {
        if (plugin.getDatabase() != null) {
            Async.run(() -> {
                UUID id = plugin.getOfflinePlayerCache().getId(targetName);
                if (id == null) {
                    sendSync(sender, ChatColor.RED + "Player not found.");
                    return;
                }
                double bal = plugin.getDatabase().getBalance(id);
                sendSync(sender, ChatColor.AQUA + targetName + ChatColor.GREEN + "'s balance: $" + format(bal));
            });
            return;
        }
        if (vault != null) {
            OfflinePlayer off = Bukkit.getOfflinePlayer(targetName);
            double bal = safeVaultBalance(off);
            sender.sendMessage(ChatColor.AQUA + targetName + ChatColor.GREEN + "'s balance: $" + format(bal));
            return;
        }
        sender.sendMessage(ChatColor.RED + "No economy backend available.");
    }

    private double safeVaultBalance(OfflinePlayer player) {
        try {
            return vault.getBalance(player);
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private String format(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    private void sendSync(CommandSender target, String msg) {
        if (Bukkit.isPrimaryThread()) {
            target.sendMessage(msg);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> target.sendMessage(msg));
        }
    }
}
