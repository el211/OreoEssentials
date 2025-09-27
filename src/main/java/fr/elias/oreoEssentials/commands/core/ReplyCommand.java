package fr.elias.oreoEssentials.commands.core;



import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ReplyCommand implements OreoCommand {
    private final MessageService messages;

    public ReplyCommand(MessageService messages) { this.messages = messages; }

    @Override public String name() { return "r"; }
    @Override public List<String> aliases() { return List.of("reply"); }
    @Override public String permission() { return "oreo.msg"; }
    @Override public String usage() { return "<message...>"; }
    @Override public boolean playerOnly() { return true; }

    @Override public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) return false;
        Player p = (Player) sender;
        var last = messages.getLast(p.getUniqueId());
        if (last == null) { p.sendMessage("§cNo one to reply to."); return true; }
        Player target = Bukkit.getPlayer(last);
        if (target == null) { p.sendMessage("§cThat player is offline."); return true; }
        String msg = String.join(" ", args);
        target.sendMessage("§7[§dMSG§7] §b" + p.getName() + "§7: §f" + msg);
        p.sendMessage("§7[§dMSG§7] §f-> §b" + target.getName() + "§7: §f" + msg);
        messages.record(p, target);
        return true;
    }
}

