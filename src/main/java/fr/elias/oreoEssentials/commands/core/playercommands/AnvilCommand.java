package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class AnvilCommand implements OreoCommand {
    @Override public String name() { return "anvil"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.anvil"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        Inventory anvil = Bukkit.createInventory(p, InventoryType.ANVIL, "§8Anvil");
        p.openInventory(anvil);
        return true;
    }
}
