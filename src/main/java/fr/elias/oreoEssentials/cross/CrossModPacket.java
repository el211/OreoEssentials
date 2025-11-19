package fr.elias.oreoEssentials.cross;

public final class CrossModPacket {

    public enum Action {
        KILL,
        KICK,
        BAN
    }

    private String type = "MOD";   // for the generic wrapper if you use one
    private Action action;
    private java.util.UUID target;
    private String targetName;
    private String reason;
    private String sourceServer;
    private String targetServer;   // optional: where we *think* they are

    // getters / setters / no-args ctor for Gson
}
