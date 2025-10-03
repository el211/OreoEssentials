package fr.elias.oreoEssentials.commands.core.playercommands;


import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class MsgCommand implements OreoCommand {
    private final MessageService messages;

    public MsgCommand(MessageService messages) { this.messages = messages; }

    @Override public String name() { return "msg"; }
    @Override public List<String> aliases() { return List.of("tell", "w"); }
    @Override public String permission() { return "oreo.msg"; }
    @Override public String usage() { return "<player> <message...>"; }
    @Override public boolean playerOnly() { return false; }

    @Override public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) return false;
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { sender.sendMessage("§cPlayer not found."); return true; }
        String msg = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        target.sendMessage("§7[§dMSG§7] §b" + sender.getName() + "§7: §f" + msg);
        sender.sendMessage("§7[§dMSG§7] §f-> §b" + target.getName() + "§7: §f" + msg);
        if (sender instanceof Player s) messages.record(s, target);
        return true;
    }
}
