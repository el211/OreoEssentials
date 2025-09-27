package fr.elias.oreoEssentials.commands.core;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.HomeService;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;

public class HomesCommand implements OreoCommand {
    private final HomeService homeService;

    public HomesCommand(HomeService homeService) {
        this.homeService = homeService;
    }

    @Override public String name() { return "homes"; }
    @Override public List<String> aliases() { return List.of("listhomes"); }
    @Override public String permission() { return "oreo.homes"; }
    @Override public String usage() { return "[name]"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;

        if (args.length == 1) {
            // Shortcut: /homes <name> -> run /home <name>
            String home = args[0];
            p.performCommand("home " + home);
            return true;
        }

        Set<String> homes = fetchHomeNamesReflective(p.getUniqueId());
        int count = homes.size();

        p.sendMessage(org.bukkit.ChatColor.GOLD + "You have " + org.bukkit.ChatColor.AQUA + count + org.bukkit.ChatColor.GOLD + " home(s):");

        if (homes.isEmpty()) return true;

        for (String name : new TreeSet<>(homes)) {
            TextComponent line = new TextComponent(" - ");
            TextComponent homeName = new TextComponent(name);
            homeName.setColor(ChatColor.AQUA);
            TextComponent tp = new TextComponent(" [TP]");
            tp.setColor(ChatColor.GREEN);
            tp.setBold(true);
            tp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/home " + name));
            tp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("Click to teleport to ").color(ChatColor.YELLOW)
                            .append(name).color(ChatColor.AQUA).create()));

            line.addExtra(homeName);
            line.addExtra(tp);

            p.spigot().sendMessage(line);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private Set<String> fetchHomeNamesReflective(UUID uuid) {
        List<String> methodNames = List.of("listHomes", "getHomes", "homesOf", "list", "listNames", "getNames");
        for (String mName : methodNames) {
            try {
                Method m = homeService.getClass().getMethod(mName, UUID.class);
                Object result = m.invoke(homeService, uuid);
                if (result instanceof Collection<?> col) {
                    Set<String> out = new HashSet<>();
                    for (Object o : col) if (o != null) out.add(String.valueOf(o));
                    return out;
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable ignored) {
            }
        }
        return Collections.emptySet();
    }
}
