// File: src/main/java/fr/elias/oreoEssentials/chat/channel/ChatChannel.java
package fr.elias.oreoEssentials.chat.channel;

import java.util.List;
import java.util.regex.Pattern;

public class ChatChannel {
    private final String name;
    private final String display;
    private final boolean readOnly;
    private final boolean crossServer;
    private final boolean defaultAutoJoin;
    private final String permissionJoin;
    private final String permissionLeave;
    private final String permissionSpeak;
    private final List<Pattern> routePatterns;
    private final String routeFormat;

    public ChatChannel(String name, String display, boolean readOnly, boolean crossServer, boolean defaultAutoJoin,
                       String permissionJoin, String permissionLeave, String permissionSpeak,
                       List<Pattern> routePatterns, String routeFormat) {
        this.name = name;
        this.display = (display == null ? name : display);
        this.readOnly = readOnly;
        this.crossServer = crossServer;
        this.defaultAutoJoin = defaultAutoJoin;
        this.permissionJoin = permissionJoin == null ? "" : permissionJoin;
        this.permissionLeave = permissionLeave == null ? "" : permissionLeave;
        this.permissionSpeak = permissionSpeak == null ? "" : permissionSpeak;
        this.routePatterns = routePatterns;
        this.routeFormat = routeFormat == null ? "&7[%channel%] %message%" : routeFormat;
    }

    // --- getters used across your code ---
    public String getName() { return name; }
    public String getDisplay() { return display; }
    public String getDisplayName() { return display; }         // <- alias for your listener
    public boolean isReadOnly() { return readOnly; }
    public boolean isCrossServer() { return crossServer; }
    public boolean isDefaultAutoJoin() { return defaultAutoJoin; }

    public String getPermissionJoin() { return permissionJoin; }
    public String getPermissionLeave() { return permissionLeave; }
    public String getPermissionSpeak() { return permissionSpeak; }

    public List<Pattern> getRoutePatterns() { return routePatterns; }
    public String getRouteFormat() { return routeFormat; }
    public String getFormat() { return routeFormat; }          // <- alias for your listener

    /** Does this channel accept the given meta/system line (e.g. "player_join: ...")? */
    public boolean matches(String raw) {
        if (raw == null || routePatterns == null) return false;
        for (Pattern p : routePatterns) {
            if (p != null && p.matcher(raw).find()) return true;
        }
        return false;
    }
}
