// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/TpAcceptCommand.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.TeleportService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class TpAcceptCommand implements OreoCommand {
    private final TeleportService tpa;

    public TpAcceptCommand(TeleportService tpa) { this.tpa = tpa; }

    @Override public String name() { return "tpaccept"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.tpa"; }
    @Override public String usage() { return "[player]"; } // optional arg in case you add it later
    @Override public boolean playerOnly() { return true; }

    // ---- debug helpers (same pattern as /tpa) ----
    private static String traceId() {
        return Long.toString(ThreadLocalRandom.current().nextLong(2176782336L), 36).toUpperCase(Locale.ROOT);
    }
    private boolean dbg() {
        try {
            var c = OreoEssentials.get().getConfig();
            return c.getBoolean("features.tpa.debug", c.getBoolean("debug", false));
        } catch (Throwable ignored) { return false; }
    }
    private boolean echo() {
        try { return OreoEssentials.get().getConfig().getBoolean("features.tpa.debug-echo-to-player", false); }
        catch (Throwable ignored) { return false; }
    }
    private void D(String id, String msg) { if (dbg()) OreoEssentials.get().getLogger().info("[TPACCEPT " + id + "] " + msg); }
    private void E(String id, String msg, Throwable t) { if (dbg()) OreoEssentials.get().getLogger().log(Level.WARNING, "[TPACCEPT " + id + "] " + msg, t); }
    private void P(Player p, String id, String msg) { if (dbg() && echo()) p.sendMessage("§8[§bTPA§8/§7" + id + "§8] §7" + msg); }
    private static String ms(long startNanos) {
        long ms = (System.nanoTime() - startNanos) / 1_000_000L;
        return ms + "ms";
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player target)) return true;

        final String id = traceId();
        final long t0 = System.nanoTime();
        final String server = OreoEssentials.get().getConfigService().serverName();

        D(id, "enter player=" + target.getName() + " server=" + server);

        // 1) Cross-server accept path
        try {
            var broker = OreoEssentials.get().getTpaBroker();
            if (broker == null) {
                D(id, "broker=null (PacketManager disabled or not initialized)");
            } else {
                long t1 = System.nanoTime();
                boolean handled = broker.acceptCrossServer(target);
                D(id, "broker.acceptCrossServer -> " + handled + " in " + ms(t1));
                P(target, id, "cross-server accept " + (handled ? "✓" : "–"));
                if (handled) {
                    D(id, "done in " + ms(t0));
                    return true; // fully handled by broker (includes instructing the other server / moving player)
                }
            }
        } catch (Throwable t) {
            E(id, "broker.acceptCrossServer threw", t);
        }

        // 2) Local same-server accept path
        try {
            long t2 = System.nanoTime();
            boolean ok = tpa.accept(target);
            D(id, "teleportService.accept -> " + ok + " in " + ms(t2));
            P(target, id, "local accept " + (ok ? "✓" : "–"));

            if (!ok) {
                // Mirror the vanilla message but add a helpful hint while debugging
                target.sendMessage("§cNo pending teleport requests.");
                if (dbg()) {
                    target.sendMessage("§7(If this was cross-server, ensure the request arrived on this server and hasn’t expired.)");
                }
            }
            D(id, "done in " + ms(t0));
            return true;
        } catch (Throwable t) {
            E(id, "teleportService.accept threw", t);
            target.sendMessage("§cFailed to accept teleport request. See console for details.");
            return true;
        }
    }
}
