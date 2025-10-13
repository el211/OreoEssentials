package fr.elias.oreoEssentials.mobs;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Locale;

public final class HealthBarListener implements Listener {

    private final OreoEssentials plugin;
    private final boolean enabled;
    private final String fmt;
    private final int segments;
    private final String fullCh, emptyCh;
    private final String colFull, colMid, colLow;
    private final double thMid, thLow;
    private final boolean includePassive;
    private final boolean includePlayers;
    private final boolean onlyWhenDamaged;
    private final boolean mythicEnabled;

    public HealthBarListener(OreoEssentials plugin) {
        this.plugin = plugin;

        var root = plugin.getConfig().getConfigurationSection("mobs");
        this.enabled = root != null && root.getBoolean("show-healthmobs", false);

        var hb = (root != null) ? root.getConfigurationSection("healthbar") : null;
        this.fmt = (hb != null) ? hb.getString("format", "&c❤ <bar> &7(<current>/<max>) &f<name>") : "&c❤ <bar> &7(<current>/<max>) &f<name>";
        this.segments = (hb != null) ? Math.max(1, hb.getInt("segments", 20)) : 20;
        this.fullCh   = (hb != null) ? hb.getString("full", "▰") : "▰";
        this.emptyCh  = (hb != null) ? hb.getString("empty", "▱") : "▱";
        this.colFull  = (hb != null) ? hb.getString("color-full", "&a") : "&a";
        this.colMid   = (hb != null) ? hb.getString("color-mid", "&e") : "&e";
        this.colLow   = (hb != null) ? hb.getString("color-low", "&c") : "&c";
        this.thMid    = (hb != null) ? clamp01(hb.getDouble("mid-threshold", 0.5)) : 0.5;
        this.thLow    = (hb != null) ? clamp01(hb.getDouble("low-threshold", 0.2)) : 0.2;
        this.includePassive   = (hb != null) && hb.getBoolean("include-passive", true);
        this.includePlayers   = (hb != null) && hb.getBoolean("include-players", false);
        this.onlyWhenDamaged  = (hb != null) && hb.getBoolean("only-when-damaged", false);
        this.mythicEnabled    = (hb != null) && hb.getBoolean("use-mythicmobs", true);
    }

    public boolean isEnabled() { return enabled; }

    public void reload() {
        // No state to clear; names are updated live on next event.
    }

    /* ---------------- Events ---------------- */

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!enabled) return;
        var ent = e.getEntity();
        if (!shouldTrack(ent)) return;
        if (onlyWhenDamaged) return; // delay until first damage
        update(ent);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent e) {
        if (!enabled) return;
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        if (!shouldTrack(le)) return;

        // Delay update 1 tick so Bukkit applies the new health value before we render
        new BukkitRunnable() {
            @Override public void run() { update(le); }
        }.runTask(plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onRegain(EntityRegainHealthEvent e) {
        if (!enabled) return;
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        if (!shouldTrack(le)) return;

        new BukkitRunnable() {
            @Override public void run() { update(le); }
        }.runTask(plugin);
    }

    /* ---------------- Core ---------------- */

    private boolean shouldTrack(LivingEntity le) {
        if (le instanceof Player) return includePlayers;
        if (!includePassive && isPassive(le.getType())) return false;
        return true;
    }

    private void update(LivingEntity le) {
        double cur = Math.max(0.0, le.getHealth());
        double max = getMaxHealth(le);
        if (max <= 0) max = 20.0;

        String mobName = mythicEnabled ? MythicMobsHook.tryName(le) : null;
        if (mobName == null || mobName.isEmpty()) {
            String base = le.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            mobName = Character.toUpperCase(base.charAt(0)) + base.substring(1);
        }

        String bar = buildBar(cur, max);
        String rendered = fmt
                .replace("<bar>", bar)
                .replace("<current>", formatHp(cur))
                .replace("<max>", formatHp(max))
                .replace("<name>", mobName);

        le.setCustomName(ChatColor.translateAlternateColorCodes('&', rendered));
        le.setCustomNameVisible(true);
    }

    private String buildBar(double cur, double max) {
        double ratio = (max <= 0 ? 0 : cur / max);
        ratio = Math.max(0, Math.min(1, ratio));
        int fullCount = (int) Math.round(ratio * segments);
        StringBuilder sb = new StringBuilder(segments * 2);

        String col = colFor(ratio);
        for (int i = 0; i < segments; i++) {
            sb.append(i < fullCount ? fullCh : emptyCh);
        }
        return ChatColor.translateAlternateColorCodes('&', col) + sb;
    }

    private String colFor(double r) {
        if (r <= thLow) return colLow;
        if (r <= thMid) return colMid;
        return colFull;
    }

    private static String formatHp(double v) {
        // integers look nicer (10 instead of 10.0)
        long l = (long) v;
        return (Math.abs(v - l) < 0.0001) ? Long.toString(l) : String.format(Locale.US, "%.1f", v);
    }

    private static double getMaxHealth(LivingEntity le) {
        // try via Attribute enum without compiling against a specific constant
        try {
            org.bukkit.attribute.Attribute key =
                    org.bukkit.attribute.Attribute.valueOf("GENERIC_MAX_HEALTH");
            var inst = le.getAttribute(key);
            if (inst != null) return inst.getValue();
        } catch (Throwable ignored) {
            // fall through
        }

        // legacy/compat fallback (works on older APIs)
        try { return le.getMaxHealth(); } catch (Throwable ignored) {}

        // last resort
        return 20.0;
    }


    private static double clamp01(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return 0;
        return Math.max(0, Math.min(1, d));
    }

    private static boolean isPassive(EntityType t) {
        // quick-and-simple passive list
        switch (t) {
            case SHEEP: case COW: case PIG: case CHICKEN: case RABBIT:
            case HORSE: case DONKEY: case MULE: case VILLAGER:
            case SQUID: case GLOW_SQUID: case FOX: case CAT:
            case TURTLE: case STRIDER: case SNIFFER: case CAMEL:
            case BEE: case PARROT:
                return true;
            default: return false;
        }
    }
}
