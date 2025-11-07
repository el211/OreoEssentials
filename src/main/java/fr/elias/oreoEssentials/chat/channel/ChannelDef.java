// File: src/main/java/fr/elias/oreoEssentials/chat/channel/ChannelDef.java
package fr.elias.oreoEssentials.chat.channel;

public class ChannelDef {
    public final String id;
    public final String display;
    public final boolean readOnly;
    public final boolean crossServer;
    public final boolean autojoinDefault;
    public final Permissions permissions;

    public static class Permissions {
        public final String join;
        public final String leave;
        public final String speak;
        public Permissions(String join, String leave, String speak) {
            this.join = join == null ? "" : join;
            this.leave = leave == null ? "" : leave;
            this.speak = speak == null ? "" : speak;
        }
    }

    public ChannelDef(String id, String display, boolean readOnly,
                      boolean crossServer, boolean autojoinDefault,
                      Permissions permissions) {
        this.id = id;
        this.display = display == null ? id : display;
        this.readOnly = readOnly;
        this.crossServer = crossServer;
        this.autojoinDefault = autojoinDefault;
        this.permissions = permissions == null ? new Permissions("", "", "") : permissions;
    }
}
