package fr.elias.oreoEssentials.holograms;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.Optional;

public final class BlockOreoHologram extends OreoHologram {

    // Static keys: no need to reach into your manager or main plugin class.
    private static final NamespacedKey K_IS_OREO = NamespacedKey.fromString("oreoessentials:oreo_hologram");
    private static final NamespacedKey K_NAME    = NamespacedKey.fromString("oreoessentials:name");
    private static final NamespacedKey K_TYPE    = NamespacedKey.fromString("oreoessentials:type");

    public BlockOreoHologram(String name, OreoHologramData data) {
        super(name, data);
    }

    /** Try to find an already-tagged BlockDisplay for this hologram name near the target location / in the chunk. */
    private Optional<BlockDisplay> findTaggedExisting() {
        var loc = data.location.toLocation();
        if (loc == null || loc.getWorld() == null) return Optional.empty();

        final var world = loc.getWorld();
        final String nameKey = getName().toLowerCase(Locale.ROOT);

        // Fast radius search
        for (var e : world.getNearbyEntities(loc, 3, 3, 3, ent -> ent instanceof BlockDisplay)) {
            var bd = (BlockDisplay) e;
            PersistentDataContainer pdc = bd.getPersistentDataContainer();
            if (pdc.has(K_IS_OREO, PersistentDataType.BYTE)) {
                String n = pdc.get(K_NAME, PersistentDataType.STRING);
                if (nameKey.equals(n)) return Optional.of(bd);
            }
        }

        // Fallback: scan the chunk
        for (var e : world.getChunkAt(loc).getEntities()) {
            if (e instanceof BlockDisplay bd) {
                var pdc = bd.getPersistentDataContainer();
                if (pdc.has(K_IS_OREO, PersistentDataType.BYTE)) {
                    String n = pdc.get(K_NAME, PersistentDataType.STRING);
                    if (nameKey.equals(n)) return Optional.of(bd);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void spawnIfMissing() {
        var loc = data.location.toLocation();
        if (loc == null) return;
        if (findEntity().isPresent()) return;

        // Re-attach to an existing tagged entity if present
        var existing = findTaggedExisting();
        BlockDisplay bd;
        if (existing.isPresent()) {
            bd = existing.get();
            entityId = bd.getUniqueId();
        } else {
            // Spawn & tag
            bd = (BlockDisplay) loc.getWorld().spawnEntity(loc, EntityType.BLOCK_DISPLAY);
            entityId = bd.getUniqueId();

            var pdc = bd.getPersistentDataContainer();
            pdc.set(K_IS_OREO, PersistentDataType.BYTE, (byte) 1);
            pdc.set(K_NAME, PersistentDataType.STRING, getName().toLowerCase(Locale.ROOT));
            pdc.set(K_TYPE, PersistentDataType.STRING, "BLOCK");
        }

        applyTransform();
        applyCommon();       // sets billboard, view range, scale, brightness, etc.
        applyVisibility();

        Material m = (data.blockType == null || data.blockType.isEmpty())
                ? Material.STONE
                : Material.matchMaterial(data.blockType);
        if (m == null) m = Material.STONE;
        bd.setBlock(m.createBlockData());

        if (data.updateIntervalTicks < 0) data.updateIntervalTicks = 0; // no forced ticking here
    }

    @Override
    public void despawn() {
        // Remove ALL tagged copies with this hologram name (avoid orphans)
        var loc = data.location.toLocation();
        if (loc != null && loc.getWorld() != null) {
            String nameKey = getName().toLowerCase(Locale.ROOT);
            var world = loc.getWorld();
            for (var bd : world.getEntitiesByClass(BlockDisplay.class)) {
                var pdc = bd.getPersistentDataContainer();
                if (pdc.has(K_IS_OREO, PersistentDataType.BYTE)) {
                    String n = pdc.get(K_NAME, PersistentDataType.STRING);
                    if (nameKey.equals(n)) {
                        try { bd.remove(); } catch (Throwable ignored) {}
                    }
                }
            }
        }
        // Also remove the tracked one and clear id
        findEntity().ifPresent(e -> { try { e.remove(); } catch (Throwable ignored) {} });
        entityId = null;
    }

    @Override
    public Location currentLocation() {
        var e = findEntity();
        return e.map(Entity::getLocation).orElseGet(() -> data.location.toLocation());
    }

    @Override
    protected void applyTransform() {
        findEntity().ifPresent(e -> {
            var l = data.location.toLocation();
            if (l != null) e.teleport(l);
        });
    }

    @Override
    protected void applyCommon() {
        findEntity().ifPresent(e -> commonDisplayTweaks((Display) e));
    }

    @Override
    protected void applyVisibility() {
        // Global entity for now.
    }

    public void setBlockType(String t) {
        data.blockType = t;
        findEntity().ifPresent(e -> {
            var bd = (BlockDisplay) e;
            var m = Material.matchMaterial(t);
            if (m != null) bd.setBlock(m.createBlockData());
        });
    }
}
