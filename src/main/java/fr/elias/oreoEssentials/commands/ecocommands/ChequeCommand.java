package fr.elias.oreoEssentials.commands.ecocommands;


import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.util.Async;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class ChequeCommand implements CommandExecutor, Listener {

    private final OreoEssentials plugin;
    private final Economy economy;
    private final NamespacedKey chequeKey;

    public ChequeCommand(OreoEssentials plugin) {
        this.plugin = plugin;
        this.economy = plugin.getServer().getServicesManager().getRegistration(Economy.class).getProvider();
        this.chequeKey = new NamespacedKey(plugin, "cheque_amount");
        plugin.getServer().getPluginManager().registerEvents(this, plugin); // Register event listener
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        // ✅ Check if player has permission to create cheques
        if (!player.hasPermission("OreoEssentials.cheque.create")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to create cheques.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: /cheque <amount>");
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[0]);
            if (amount <= 0) {
                player.sendMessage(ChatColor.RED + "Amount must be greater than zero.");
                return true;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid amount.");
            return true;
        }

        UUID playerUUID = player.getUniqueId();
        double balance = plugin.getDatabase().getBalance(playerUUID);

        if (balance < amount) {
            player.sendMessage(ChatColor.RED + "Insufficient funds.");
            return true;
        }

        CompletableFuture.runAsync(() -> {
            // ✅ Deduct money from balance and sync with Vault
            plugin.getDatabase().setBalance(playerUUID, player.getName(), balance - amount);

            // ✅ Create cheque item
            ItemStack cheque = createCheque(amount);
            player.getInventory().addItem(cheque);

            // ✅ Custom message from `config.yml`
            String chequeCreatedMessage = plugin.getConfig().getString("messages.cheque-created",
                    "You have created a cheque for {amount} USD!");
            chequeCreatedMessage = chequeCreatedMessage.replace("{amount}", String.valueOf(amount));

            player.sendMessage(ChatColor.GREEN + chequeCreatedMessage);
            plugin.getLogger().info(player.getName() + " created a cheque for $" + amount);
        });

        return true;
    }

    // ✅ Creates a cheque (paper item) with stored amount
    private ItemStack createCheque(double amount) {
        ItemStack cheque = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = cheque.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Cheque: $" + amount);
            meta.setLore(Arrays.asList(ChatColor.YELLOW + "Right-click to redeem this cheque."));
            meta.getPersistentDataContainer().set(chequeKey, PersistentDataType.DOUBLE, amount);
            cheque.setItemMeta(meta);
        }
        return cheque;
    }

    // ✅ Handles right-click to redeem cheque
    @EventHandler
    public void onPlayerUseCheque(PlayerInteractEvent event) {
        if (!event.getAction().name().contains("RIGHT_CLICK")) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        // ✅ Check if item is a cheque
        if (!meta.getPersistentDataContainer().has(chequeKey, PersistentDataType.DOUBLE)) {
            return;
        }

        // ✅ Check if player has permission to redeem cheques
        if (!player.hasPermission("OreoEssentials.cheque.redeem")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to redeem cheques.");
            return;
        }

        double amount = meta.getPersistentDataContainer().get(chequeKey, PersistentDataType.DOUBLE);
        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + "This cheque has an invalid amount.");
            return;
        }

        Async.run(() -> {
            UUID playerUUID = player.getUniqueId();
            double balance = plugin.getDatabase().getBalance(playerUUID);

            // ✅ Add money to player's balance and sync with Vault
            plugin.getDatabase().setBalance(playerUUID, player.getName(), balance + amount);

            // ✅ Custom message from `config.yml`
            String chequeRedeemedMessage = plugin.getConfig().getString("messages.cheque-redeemed",
                    "You have redeemed a cheque for {amount} USD!");
            chequeRedeemedMessage = chequeRedeemedMessage.replace("{amount}", String.valueOf(amount));

            player.sendMessage(ChatColor.GREEN + chequeRedeemedMessage);
            plugin.getLogger().info(player.getName() + " redeemed a cheque for $" + amount);

            // ✅ Prevent duplication exploits
            event.setCancelled(true);
            item.setAmount(item.getAmount() - 1);
        });
    }
}
