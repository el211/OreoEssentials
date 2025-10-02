package fr.elias.oreoEssentials.commands.ecocommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PayCommand implements OreoCommand {

    @Override public String name() { return "pay"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.pay"; }
    @Override public String usage() { return "<player> <amount>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use /" + label + ".");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <player> <amount>");
            return true;
        }

        var econ = OreoEssentials.get().getVaultEconomy();
        if (econ == null) {
            sender.sendMessage(ChatColor.RED + "Economy is not available.");
            return true;
        }

        // Resolve recipient as OfflinePlayer (works cross-server/offline)
        String targetArg = args[0];
        OfflinePlayer target = null;

        var cache = OreoEssentials.get().getOfflinePlayerCache();
        if (cache != null) {
            UUID id = cache.resolveNameToUuid(targetArg);
            if (id != null) target = Bukkit.getOfflinePlayer(id);
        }
        if (target == null) target = Bukkit.getOfflinePlayer(targetArg);

        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + targetArg);
            return true;
        }

        if (player.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "You can't pay yourself.");
            return true;
        }

        // Parse amount safely
        double amount;
        try {
            String raw = args[1].replace(",", "").toLowerCase(Locale.ROOT);
            if (raw.startsWith("-") || raw.contains("e")) throw new NumberFormatException();
            amount = new BigDecimal(raw).setScale(2, RoundingMode.DOWN).doubleValue();
        } catch (Exception ex) {
            sender.sendMessage(ChatColor.RED + "Invalid amount. Example: 10 or 10.50");
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "Amount must be greater than zero.");
            return true;
        }

        // Balance check (float epsilon to avoid tiny rounding mismatches)
        double balance = econ.getBalance(player);
        if (balance + 1e-9 < amount) {
            sender.sendMessage(ChatColor.RED + "Insufficient funds.");
            sender.sendMessage(ChatColor.GRAY + "Your balance: " + ChatColor.GREEN + econ.format(balance));
            return true;
        }

        // Ensure recipient account exists (some Vault providers require this)
        try {
            if (!econ.hasAccount(target)) {
                econ.createPlayerAccount(target);
            }
        } catch (Throwable ignored) {
            // Some providers don't implement hasAccount; deposit will still create it
        }

        EconomyResponse withdraw = econ.withdrawPlayer(player, amount);
        if (withdraw == null || withdraw.type != EconomyResponse.ResponseType.SUCCESS) {
            sender.sendMessage(ChatColor.RED + "Could not withdraw funds: "
                    + (withdraw != null ? withdraw.errorMessage : "unknown error"));
            return true;
        }

        EconomyResponse deposit = econ.depositPlayer(target, amount);
        if (deposit == null || deposit.type != EconomyResponse.ResponseType.SUCCESS) {
            // Refund on failure
            econ.depositPlayer(player, amount);
            sender.sendMessage(ChatColor.RED + "Could not deposit funds: "
                    + (deposit != null ? deposit.errorMessage : "unknown error"));
            return true;
        }

        String targetDisplay = (target.getName() != null ? target.getName() : targetArg);
        sender.sendMessage(ChatColor.GREEN + "Sent " + ChatColor.YELLOW + econ.format(amount)
                + ChatColor.GREEN + " to " + ChatColor.AQUA + targetDisplay + ChatColor.GREEN + ".");

        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage(ChatColor.GREEN + "You received "
                    + ChatColor.YELLOW + econ.format(amount)
                    + ChatColor.GREEN + " from " + ChatColor.AQUA + player.getName());
        }

        return true;
    }
}
