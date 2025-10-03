package fr.elias.oreoEssentials.commands.ecocommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.offline.OfflinePlayerCache;
import net.milkbowl.vault.economy.Economy;
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
        if (!(sender instanceof Player from)) {
            sender.sendMessage(ChatColor.RED + "Only players can use /" + label + ".");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <player> <amount>");
            return true;
        }

        Economy econ = OreoEssentials.get().getVaultEconomy();
        if (econ == null) {
            sender.sendMessage(ChatColor.RED + "Economy is not available.");
            return true;
        }

        // Resolve target as OfflinePlayer (works cross-server)
        OfflinePlayer target = resolveOffline(args[0]);
        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }
        if (from.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "You can't pay yourself.");
            return true;
        }

        // Parse amount safely (no scientific / negatives)
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

        double balance = econ.getBalance(from);
        if (balance + 1e-9 < amount) {
            sender.sendMessage(ChatColor.RED + "Insufficient funds.");
            sender.sendMessage(ChatColor.GRAY + "Your balance: " + ChatColor.GREEN + econ.format(balance));
            return true;
        }

        // Ensure target account exists for providers that need it
        try { if (!econ.hasAccount(target)) econ.createPlayerAccount(target); } catch (Throwable ignored) {}

        EconomyResponse w = econ.withdrawPlayer(from, amount);
        if (w == null || w.type != EconomyResponse.ResponseType.SUCCESS) {
            sender.sendMessage(ChatColor.RED + "Could not withdraw funds: " + (w != null ? w.errorMessage : "unknown error"));
            return true;
        }

        EconomyResponse d = econ.depositPlayer(target, amount);
        if (d == null || d.type != EconomyResponse.ResponseType.SUCCESS) {
            econ.depositPlayer(from, amount); // refund on failure
            sender.sendMessage(ChatColor.RED + "Could not deposit funds: " + (d != null ? d.errorMessage : "unknown error"));
            return true;
        }

        String targetName = target.getName() != null ? target.getName() : args[0];
        sender.sendMessage(ChatColor.GREEN + "Sent " + ChatColor.YELLOW + econ.format(amount)
                + ChatColor.GREEN + " to " + ChatColor.AQUA + targetName + ChatColor.GREEN + ".");

        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage(ChatColor.GREEN + "You received "
                    + ChatColor.YELLOW + econ.format(amount)
                    + ChatColor.GREEN + " from " + ChatColor.AQUA + from.getName());
        }

        // Learn mapping in cache for future /pay tab-complete
        OfflinePlayerCache cache = OreoEssentials.get().getOfflinePlayerCache();
        if (cache != null && target.getUniqueId() != null && targetName != null) {
            cache.add(targetName, target.getUniqueId());
        }
        return true;
    }

    private OfflinePlayer resolveOffline(String nameOrUuid) {
        Player p = Bukkit.getPlayerExact(nameOrUuid);
        if (p != null) return p;

        OfflinePlayerCache cache = OreoEssentials.get().getOfflinePlayerCache();
        if (cache != null) {
            UUID id = cache.getId(nameOrUuid);
            if (id != null) return Bukkit.getOfflinePlayer(id);
        }

        try { return Bukkit.getOfflinePlayer(UUID.fromString(nameOrUuid)); }
        catch (IllegalArgumentException ignored) {}

        return Bukkit.getOfflinePlayer(nameOrUuid);
    }
}
