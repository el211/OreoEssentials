package fr.elias.oreoEssentials.commands.ecocommands;


import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.SendRemoteMessagePacket;
import fr.elias.oreoEssentials.util.Async;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PayCommand implements CommandExecutor {

    private final OreoEssentials plugin;
    private final Economy economy;

    public PayCommand(OreoEssentials plugin) {
        this.plugin = plugin;
        this.economy = plugin.getServer().getServicesManager().getRegistration(Economy.class).getProvider();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Only players can use this command
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        Player senderPlayer = (Player) sender;

        // Argument validation
        if (args.length < 2) {
            senderPlayer.sendMessage(ChatColor.RED + "Usage: /pay <player> <amount>");
            return true;
        }

        String targetName = args[0];
        double amount;

        try {
            amount = Double.parseDouble(args[1]);
            if (amount <= 0) {
                senderPlayer.sendMessage(ChatColor.RED + "Amount must be greater than zero.");
                return true;
            }
        } catch (NumberFormatException e) {
            senderPlayer.sendMessage(ChatColor.RED + "Invalid amount.");
            return true;
        }

        // Run logic async to avoid blocking main thread
        Async.run(() -> {
            try {
                if (!plugin.getOfflinePlayerCache().contains(targetName)) {
                    senderPlayer.sendMessage(ChatColor.RED + "Player not found (not cached).");
                    return;
                }

                UUID targetId = plugin.getOfflinePlayerCache().getId(targetName);
                UUID senderId = senderPlayer.getUniqueId();


                if (senderId.equals(targetId)) {
                    senderPlayer.sendMessage(ChatColor.RED + "You cannot pay yourself.");
                    return;
                }

                double senderBalance = plugin.getDatabase().getBalance(senderId);

                if (senderBalance < amount) {
                    senderPlayer.sendMessage(ChatColor.RED + "You do not have enough funds.");
                    return;
                }

                // Update balances in database (and Redis)
                plugin.getDatabase().setBalance(senderId, senderPlayer.getName(), senderBalance - amount);
                double receiverBalance = plugin.getDatabase().getBalance(targetId);
                plugin.getDatabase().setBalance(targetId, targetName, receiverBalance + amount);

                // Get actual case-sensitive player name
                String resolvedName = plugin.getOfflinePlayerCache().getName(targetId);

                // Build and send message to receiver
                String rawMessage = plugin.getConfig().getString("messages.pay-received",
                        "{player} has paid you {amount} USD!");
                String formattedMessage = rawMessage
                        .replace("{player}", senderPlayer.getName())
                        .replace("{amount}", String.valueOf(amount));

                plugin.getPacketManager().sendPacket(new SendRemoteMessagePacket(targetId, ChatColor.GREEN + formattedMessage));

                // Send confirmation to sender
                senderPlayer.sendMessage(ChatColor.GREEN + "You paid " + resolvedName + " $" + amount);

                plugin.getLogger().info(senderPlayer.getName() + " paid " + resolvedName + " $" + amount);

            } catch (Exception e) {
                e.printStackTrace();
                senderPlayer.sendMessage(ChatColor.RED + "An error occurred while processing your payment.");
            }
        });

        return true;
    }
}
