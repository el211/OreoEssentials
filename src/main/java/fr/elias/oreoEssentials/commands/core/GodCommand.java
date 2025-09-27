package fr.elias.oreoEssentials.commands.core;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.GodService;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class GodCommand implements OreoCommand {
    private final GodService god;

    public GodCommand(GodService god) {
        this.god = god;
    }

    @Override public String name() { return "god"; }
    @Override public List<String> aliases() { return List.of(".god"); } // optional dot alias
    @Override public String permission() { return "oreo.god"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;

        boolean enabled = god.toggle(p.getUniqueId());

        // Also flip Bukkit’s invulnerable flag for extra safety (doesn’t cover hunger/air, hence the listener)
        try { p.setInvulnerable(enabled); } catch (Throwable ignored) {}

        if (enabled) {
            p.sendMessage(ChatColor.GREEN + "God mode " + ChatColor.AQUA + "enabled" + ChatColor.GREEN + ". You are now unbeatable.");
            // top up instantly
            p.setHealth(Math.min(p.getMaxHealth(), p.getMaxHealth()));
            p.setFoodLevel(20);
            p.setSaturation(20f);
            p.setFireTicks(0);
        } else {
            p.sendMessage(ChatColor.RED + "God mode " + ChatColor.AQUA + "disabled" + ChatColor.RED + ".");
        }
        return true;
    }
}
