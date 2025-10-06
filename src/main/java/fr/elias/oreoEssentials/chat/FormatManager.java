package fr.elias.oreoEssentials.chat;

import net.luckperms.api.LuckPermsProvider;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormatManager {
    private final CustomConfig customYmlManager;
    private static final Pattern HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");

    public FormatManager(CustomConfig customYmlManager) {
        this.customYmlManager = customYmlManager;
    }

    public String formatMessage(Player p, String message) {
        String group;
        try {
            group = LuckPermsProvider.get().getUserManager()
                    .getUser(p.getUniqueId())
                    .getPrimaryGroup();
        } catch (Throwable t) {
            group = "default";
        }

        // Load format for group or fallback
        String format = customYmlManager.getCustomConfig()
                .getString("chat." + group);

        if (format == null) {
            format = customYmlManager.getCustomConfig()
                    .getString("chat.default", "&7%player_displayname% » &f%chat_message%");
        }

        format = colorize(format);

        // Always inject the raw chat message ourselves
        format = format.replace("%chat_message%", message);

        // If config uses PAPI (%player_displayname%), leave it for AsyncChatListener to resolve.
        // If it uses %player_name%, prefer the DISPLAY name so /nick shows in chat.
        if (!format.contains("%player_displayname%") && format.contains("%player_name%")) {
            format = format.replace("%player_name%", p.getDisplayName());
        }

        return format;
    }

    private String colorize(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder mc = new StringBuilder("§x");
            for (char c : hex.toCharArray()) mc.append('§').append(c);
            matcher.appendReplacement(buffer, mc.toString());
        }
        matcher.appendTail(buffer);
        return buffer.toString().replace('&', '§');
    }
}
