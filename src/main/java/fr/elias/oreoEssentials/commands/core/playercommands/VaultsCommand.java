package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class VaultsCommand implements OreoCommand {

    @Override public String name() { return "oevault"; }
    @Override public List<String> aliases() { return List.of("vault", "vaults", "pv", "pvault"); }
    @Override public String permission() { return "oreo.vault.menu"; }
    @Override public String usage() { return "[menu|<id>]"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        var svc = OreoEssentials.get().getPlayervaultsService();
        if (svc == null || !svc.enabled()) {
            p.sendMessage(ChatColor.RED + "PlayerVaults are disabled.");
            return true;
        }

        // /oevault or /oevault menu -> open the menu
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            svc.openMenu(p);
            return true;
        }

        // /oevault <id> -> open a specific vault
        try {
            int id = Integer.parseInt(args[0]);
            if (id <= 0) throw new NumberFormatException();
            svc.openVault(p, id);
        } catch (NumberFormatException ex) {
            p.sendMessage(ChatColor.RED + "Usage: /" + label + " " + usage());
        }
        return true;
    }
}
