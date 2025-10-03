package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NickCommand implements OreoCommand, TabCompleter {
    private static final int MAX_LEN = 16; // vanilla tab-list limit
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    @Override public String name() { return "nick"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.nick"; }
    @Override public String usage() { return "<newName> | unnick"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;

        if (args.length < 1) {
            p.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <newName>  or  /" + label + " unnick");
            return true;
        }

        // reset
        if (args[0].equalsIgnoreCase("unnick") || args[0].equalsIgnoreCase("reset")) {
            resetNick(p);
            p.sendMessage(ChatColor.GREEN + "Your nickname has been reset.");
            return true;
        }

        String newName = args[0];

        if (newName.length() > MAX_LEN) {
            p.sendMessage(ChatColor.RED + "Name too long (max " + MAX_LEN + " chars).");
            return true;
        }
        if (!VALID.matcher(newName).matches()) {
            p.sendMessage(ChatColor.RED + "Invalid name. Use letters, numbers, underscore (3–16).");
            return true;
        }

        // Chat + TAB list (Bukkit API)
        try { p.setDisplayName(newName); } catch (Throwable ignored) {}
        try { p.setPlayerListName(newName); } catch (Throwable ignored) {}

        p.sendMessage(ChatColor.GREEN + "You are now nicked as " + ChatColor.AQUA + newName + ChatColor.GREEN + ".");
        return true;
    }

    private void resetNick(Player p) {
        String real = p.getName();
        try { p.setDisplayName(real); } catch (Throwable ignored) {}
        try { p.setPlayerListName(real); } catch (Throwable ignored) {}
    }

    // --- Tab completion ---
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("unnick", "reset").stream()
                    .filter(opt -> opt.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
