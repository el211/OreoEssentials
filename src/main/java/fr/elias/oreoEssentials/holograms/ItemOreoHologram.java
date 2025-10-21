package fr.elias.oreoEssentials.holograms;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

public final class ItemOreoHologram extends OreoHologram {

    // Static keys
    private static final NamespacedKey K_IS_OREO = NamespacedKey.fromString("oreoessentials:oreo_hologram");
    private static final NamespacedKey K_NAME    = NamespacedKey.fromString("oreoessentials:name");
    private static final NamespacedKey K_TYPE    = NamespacedKey.fromString("oreoessentials:type");

    public ItemOreoHologram(String name, OreoHologramData data) {
        super(name, data);
    }

    /** Try to find an already-tagged ItemDisplay for this hologram name near the target location / in the chunk. */
    private Optional<ItemDisplay> findTaggedExisting() {
        var loc = data.location.toLocation();
        if (loc == null || loc.getWorld() == null) return Optional.empty();

        final var world = loc.getWorld();
        final String nameKey = getName().toLowerCase(Locale.ROOT);

        // Fast radius search
        for (var e : world.getNearbyEntities(loc, 3, 3, 3, ent -> ent instanceof ItemDisplay)) {
            var id = (ItemDisplay) e;
            PersistentDataContainer pdc = id.getPersistentDataContainer();
            if (pdc.has(K_IS_OREO, PersistentDataType.BYTE)) {
                String n = pdc.get(K_NAME, PersistentDataType.STRING);
                if (nameKey.equals(n)) return Optional.of(id);
            }
        }

        // Fallback: scan the chunk
        for (var e : world.getChunkAt(loc).getEntities()) {
            if (e instanceof ItemDisplay id) {
                var pdc = id.getPersistentDataContainer();
                if (pdc.has(K_IS_OREO, PersistentDataType.BYTE)) {
                    String n = pdc.get(K_NAME, PersistentDataType.STRING);
                    if (nameKey.equals(n)) return Optional.of(id);
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

        // Re-attach if a tagged ItemDisplay already exists
        var existing = findTaggedExisting();
        ItemDisplay id;
        if (existing.isPresent()) {
            id = existing.get();
            entityId = id.getUniqueId();
        } else {
            id = (ItemDisplay) loc.getWorld().spawnEntity(loc, EntityType.ITEM_DISPLAY);
            entityId = id.getUniqueId();

            // Tag it so we can reattach / clean up next time
            var pdc = id.getPersistentDataContainer();
            pdc.set(K_IS_OREO, PersistentDataType.BYTE, (byte) 1);
            pdc.set(K_NAME, PersistentDataType.STRING, getName().toLowerCase(Locale.ROOT));
            pdc.set(K_TYPE, PersistentDataType.STRING, "ITEM");
        }

        applyTransform();
        applyCommon();   // billboard, view range, scale, brightness, etc.
        applyVisibility();

        // load stored item (might be null on first spawn)
        ItemStack stack = decodeItem(data.itemStackBase64);
        if (stack == null || stack.getType() == org.bukkit.Material.AIR) {
            stack = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_BLOCK);
        }
        id.setItemStack(stack);

        if (data.updateIntervalTicks < 0) data.updateIntervalTicks = 0;
    }

    @Override
    public void despawn() {
        // Remove ALL tagged copies with this hologram name
        var loc = data.location.toLocation();
        if (loc != null && loc.getWorld() != null) {
            String nameKey = getName().toLowerCase(Locale.ROOT);
            var world = loc.getWorld();
            for (var id : world.getEntitiesByClass(ItemDisplay.class)) {
                var pdc = id.getPersistentDataContainer();
                if (pdc.has(K_IS_OREO, PersistentDataType.BYTE)) {
                    String n = pdc.get(K_NAME, PersistentDataType.STRING);
                    if (nameKey.equals(n)) {
                        try { id.remove(); } catch (Throwable ignored) {}
                    }
                }
            }
        }
        // Also remove tracked entity & clear id
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

    public void setItem(ItemStack stack) {
        data.itemStackBase64 = encodeItem(stack);
        findEntity().ifPresent(e -> ((ItemDisplay) e).setItemStack(stack));
    }

    /* ----------------- (de)serialization helpers ----------------- */

    private static String encodeItem(ItemStack stack) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(bos)) {
            oos.writeObject(stack);
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            return "";
        }
    }

    private static ItemStack decodeItem(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getDecoder().decode(b64));
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bis)) {
            return (ItemStack) ois.readObject();
        } catch (Throwable t) {
            return null;
        }
    }
}
