package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.chat.CustomConfig;
import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;

public class AfeliusReloadCommand implements OreoCommand {
    private final OreoEssentials plugin;
    private final CustomConfig chatCfg;

    public AfeliusReloadCommand(OreoEssentials plugin, CustomConfig chatCfg) {
        this.plugin = plugin;
        this.chatCfg = chatCfg;
    }

    @Override public String name() { return "afelius"; }
    @Override public List<String> aliases() { return List.of("af"); }
    @Override public String permission() { return "oreo.afelius.reload"; }
    @Override public String usage() { return "reload <all|config|chat-format>"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " reload <all|config|chat-format>");
            return true;
        }
        String target = (args.length >= 2) ? args[1].toLowerCase() : "all";
        switch (target) {
            case "all" -> {
                plugin.reloadConfig();
                chatCfg.reloadCustomConfig();
                sender.sendMessage("§aAll configurations reloaded.");
            }
            case "config" -> {
                plugin.reloadConfig();
                sender.sendMessage("§aMain config reloaded.");
            }
            case "chat-format" -> {
                chatCfg.reloadCustomConfig();
                sender.sendMessage("§achat-format.yml reloaded.");
            }
            default -> sender.sendMessage("§cUnknown section. Use: " + String.join(", ", Arrays.asList("all","config","chat-format")));
        }
        return true;
    }
}
