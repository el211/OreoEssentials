package fr.elias.oreoEssentials.trade;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public final class TradeService {

    private final OreoEssentials plugin;
    private final TradeConfig cfg;

    // each player can be in at most one session
    private final Map<UUID, TradeSession> sessions = new HashMap<>();

    public TradeService(OreoEssentials plugin, TradeConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    public boolean inTrade(Player p) { return sessions.containsKey(p.getUniqueId()); }

    public void startTrade(Player a, Player b) {
        if (a == null || b == null) return;
        if (!a.isOnline() || !b.isOnline()) return;
        if (inTrade(a) || inTrade(b)) {
            a.sendMessage("§cOne of you is already trading.");
            return;
        }
        if (cfg.minDistanceBlocks > 0 && a.getWorld().equals(b.getWorld())) {
            if (a.getLocation().distanceSquared(b.getLocation()) > (cfg.minDistanceBlocks * cfg.minDistanceBlocks)) {
                a.sendMessage("§cThey are too far away to trade (min " + cfg.minDistanceBlocks + " blocks).");
                return;
            }
        }
        TradeSession s = new TradeSession(plugin, cfg, a, b, this::finish, this::cancel);
        sessions.put(a.getUniqueId(), s);
        sessions.put(b.getUniqueId(), s);
        s.open();
    }

    private void finish(TradeSession session) {
        // swap items safely
        var a = session.a;
        var b = session.b;

        // deliver A->B
        for (var it : session.getOfferA()) if (it != null) b.getInventory().addItem(it.clone());
        // deliver B->A
        for (var it : session.getOfferB()) if (it != null) a.getInventory().addItem(it.clone());

        // clear session
        cleanup(session);
        a.playSound(a.getLocation(), cfg.completeSound, 1f, 1f);
        b.playSound(b.getLocation(), cfg.completeSound, 1f, 1f);
        a.sendMessage("§aTrade completed!");
        b.sendMessage("§aTrade completed!");
    }

    private void cancel(TradeSession session, String reason) {
        // return items to owners
        var a = session.a;
        var b = session.b;
        for (var it : session.getOfferA()) if (it != null) a.getInventory().addItem(it.clone());
        for (var it : session.getOfferB()) if (it != null) b.getInventory().addItem(it.clone());
        cleanup(session);
        if (reason != null && !reason.isEmpty()) {
            a.sendMessage("§cTrade cancelled: §7" + reason);
            b.sendMessage("§cTrade cancelled: §7" + reason);
        }
    }

    public void cancelAll() {
        new ArrayList<>(new HashSet<>(sessions.values())).forEach(s -> cancel(s, "server stopping"));
    }

    private void cleanup(TradeSession s) {
        sessions.remove(s.a.getUniqueId());
        sessions.remove(s.b.getUniqueId());
    }
}
