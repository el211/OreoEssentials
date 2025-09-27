package fr.elias.oreoEssentials.commands.core;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.economy.EconomyBootstrap;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class PayCommand implements OreoCommand {
    private final EconomyBootstrap eco;
    public PayCommand(EconomyBootstrap eco) { this.eco = eco; }

    @Override public String name() { return "pay"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.pay"; }
    @Override public String usage() { return "<player> <amount>"; }
    @Override public boolean playerOnly() { return true; }

    @Override public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) return false;
        Player from = (Player) sender;
        Player to = Bukkit.getPlayerExact(args[0]);
        if (to == null) { from.sendMessage("§cPlayer not found."); return true; }
        if (to.equals(from)) { from.sendMessage("§cYou cannot pay yourself."); return true; }

        double amount;
        try { amount = Double.parseDouble(args[1]); } catch (NumberFormatException e) { from.sendMessage("§cInvalid amount."); return true; }
        if (amount <= 0) { from.sendMessage("§cAmount must be positive."); return true; }

        boolean ok = eco.api().transfer(from.getUniqueId(), to.getUniqueId(), amount);
        if (!ok) { from.sendMessage("§cInsufficient funds."); return true; }
        from.sendMessage("§aPaid §b" + to.getName() + " §a" + String.format("%.2f", amount));
        to.sendMessage("§aYou received §a" + String.format("%.2f", amount) + " §afrom §b" + from.getName());
        return true;
    }
}