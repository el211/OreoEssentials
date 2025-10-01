package fr.elias.oreoEssentials.chat;

import net.luckperms.api.LuckPermsProvider;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormatManager {
    private final CustomConfig customYmlManager;
    private static final Pattern HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");

    public FormatManager(CustomConfig customYmlManager) { this.customYmlManager = customYmlManager; }

    public String formatMessage(Player p, String message) {
        String group;
        try {
            group = LuckPermsProvider.get().getUserManager().getUser(p.getUniqueId()).getPrimaryGroup();
        } catch (Throwable t) {
            group = "default";
        }

        String format = customYmlManager.getCustomConfig().getString("chat." + group);
        if (format == null) format = customYmlManager.getCustomConfig().getString("chat.default", "&7%player_name% » &f%chat_message%");
        format = colorize(format).replace("%chat_message%", message).replace("%player_name%", p.getName());
        return format;
    }

    private String colorize(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hexCode = matcher.group(1);
            StringBuilder hexFormatted = new StringBuilder("§x");
            for (char c : hexCode.toCharArray()) hexFormatted.append('§').append(c);
            matcher.appendReplacement(buffer, hexFormatted.toString());
        }
        matcher.appendTail(buffer);
        return buffer.toString().replace("&", "§");
    }
}
