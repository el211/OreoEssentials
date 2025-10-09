package fr.elias.oreoEssentials.services;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AfkService {
    private final Set<UUID> afk = new HashSet<>();

    public boolean isAfk(Player p) { return afk.contains(p.getUniqueId()); }

    public boolean toggleAfk(Player p) {
        if (isAfk(p)) {
            afk.remove(p.getUniqueId());
            applyTag(p, false);
            return false;
        } else {
            afk.add(p.getUniqueId());
            applyTag(p, true);
            return true;
        }
    }

    public void clearAfk(Player p) {
        if (isAfk(p)) {
            afk.remove(p.getUniqueId());
            applyTag(p, false);
        }
    }

    private void applyTag(Player p, boolean on) {
        try {
            String base = ChatColor.stripColor(p.getPlayerListName());
            if (base == null || base.isEmpty()) base = p.getName();
            if (on) {
                p.setPlayerListName("§7[AFK] §f" + base);
            } else {
                p.setPlayerListName(base);
            }
        } catch (Throwable ignored) {}
    }
}
