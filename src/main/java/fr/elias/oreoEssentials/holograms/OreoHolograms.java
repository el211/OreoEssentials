package fr.elias.oreoEssentials.holograms;

import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime registry + persistence bridge for Oreo holograms.
 * - Loads all holograms from disk on enable
 * - Exposes CRUD helpers (/ohologram command uses these)
 * - Provides tickAll() for periodic, lightweight updates (text refresh, etc.)
 */
public final class OreoHolograms {

    private final Plugin plugin;
    private final OreoHologramsStore store;

    /** keyed by lower-cased hologram name */
    private final Map<String, OreoHologram> holos = new ConcurrentHashMap<>();

    public OreoHolograms(Plugin plugin) {
        this.plugin = plugin;
        this.store = new OreoHologramsStore(plugin.getDataFolder());
    }

    /* -------------------- lifecycle -------------------- */

    /** Load all holograms from storage and (re)spawn them. */
    public void load() {
        holos.clear();
        for (OreoHologramData d : store.loadAll()) {
            try {
                OreoHologram h = OreoHologramFactory.fromData(d);
                holos.put(h.getName().toLowerCase(Locale.ROOT), h);
                h.spawnIfMissing();
            } catch (Throwable t) {
                plugin.getLogger().warning("[OreoHolograms] Failed to load " + d.name + ": " + t.getMessage());
            }
        }
    }

    /** Save all holograms to storage. */
    public void save() {
        List<OreoHologramData> all = new ArrayList<>(holos.size());
        for (OreoHologram h : holos.values()) {
            all.add(h.toData());
        }
        store.saveAll(all);
    }

    /** Despawn everything and clear memory (called on plugin disable). */
    public void unload() {
        for (OreoHologram h : holos.values()) {
            try { h.despawn(); } catch (Throwable ignored) {}
        }
        holos.clear();
    }

    /**
     * Called by the Bukkit scheduler in OreoEssentials.onEnable().
     * Each hologram can decide if it actually needs to update.
     */
    public void tickAll() {
        for (OreoHologram h : holos.values()) {
            try { h.tick(); } catch (Throwable ignored) {}
        }
    }

    /* ---------------------- CRUD ----------------------- */

    public boolean exists(String name) {
        return holos.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public OreoHologram get(String name) {
        return holos.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<OreoHologram> all() {
        return Collections.unmodifiableCollection(holos.values());
    }

    public OreoHologram create(OreoHologramType type, String name, OreoHologramLocation loc) {
        if (exists(name)) throw new IllegalArgumentException("Hologram already exists: " + name);
        OreoHologram h = OreoHologramFactory.create(type, name, loc);
        holos.put(name.toLowerCase(Locale.ROOT), h);
        h.spawnIfMissing();
        save();
        return h;
    }

    public void remove(String name) {
        OreoHologram h = holos.remove(name.toLowerCase(Locale.ROOT));
        if (h != null) {
            try { h.despawn(); } catch (Throwable ignored) {}
            save();
        }
    }

    public OreoHologram copy(String from, String to) {
        OreoHologram src = get(from);
        if (src == null) throw new IllegalArgumentException("Source hologram not found: " + from);
        if (exists(to)) throw new IllegalArgumentException("Target hologram already exists: " + to);

        OreoHologramData srcData = deepCopy(src.toData());
        srcData.name = to;

        OreoHologram dst = OreoHologramFactory.fromData(srcData);
        holos.put(to.toLowerCase(Locale.ROOT), dst);
        dst.spawnIfMissing();
        save();
        return dst;
    }

    /* --------------- internal deep copy helper --------------- */
    /**
     * Make a safe deep copy of OreoHologramData without relying on Object.clone().
     * Adjust the fields below to match your actual OreoHologramData definition.
     */
    private OreoHologramData deepCopy(OreoHologramData d) {
        OreoHologramData c = new OreoHologramData();

        // Identity / type
        c.type = d.type;
        c.name = d.name;

        // Location (shallow copy; replace with a proper copy ctor if your class has one)
        c.location = d.location;

        // Common props
        c.scale = d.scale;
        c.billboard = d.billboard;
        c.shadowStrength = d.shadowStrength;
        c.shadowRadius = d.shadowRadius;
        c.brightnessBlock = d.brightnessBlock;
        c.brightnessSky = d.brightnessSky;
        c.visibilityDistance = d.visibilityDistance;
        c.visibility = d.visibility;
        c.viewPermission = d.viewPermission;
        c.updateIntervalTicks = d.updateIntervalTicks;
        c.manualViewers = (d.manualViewers == null) ? new ArrayList<>() : new ArrayList<>(d.manualViewers);

        // Text props (safe to set even for non-text types)
        c.backgroundColor = d.backgroundColor;
        c.textShadow = d.textShadow;
        c.textAlign = d.textAlign;
        c.lines = (d.lines == null) ? new ArrayList<>() : new ArrayList<>(d.lines);

        // Block-type (if present in your data class)
        try { c.blockType = d.blockType; } catch (Throwable ignored) {}

        // NOTE: We intentionally DO NOT touch an "item" field,
        // since your data class does not define it (avoids compile errors).

        return c;
    }
}
