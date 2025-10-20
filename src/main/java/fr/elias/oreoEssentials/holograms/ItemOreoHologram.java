package fr.elias.oreoEssentials.holograms;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.Base64;

public final class ItemOreoHologram extends OreoHologram {

    public ItemOreoHologram(String name, OreoHologramData data) { super(name, data); }

    @Override public void spawnIfMissing() {
        var loc = data.location.toLocation(); if (loc == null) return;
        if (findEntity().isPresent()) return;
        ItemDisplay id = (ItemDisplay) loc.getWorld().spawnEntity(loc, EntityType.ITEM_DISPLAY);
        entityId = id.getUniqueId();
        applyTransform(); applyCommon(); applyVisibility();
        ItemStack stack = decodeItem(data.itemStackBase64);
        if (stack != null) id.setItemStack(stack);
    }

    @Override public void despawn() { findEntity().ifPresent(e -> e.remove()); entityId = null; }
    @Override public Location currentLocation() { var e=findEntity(); return e.map(Entity::getLocation).orElseGet(()->data.location.toLocation()); }
    @Override protected void applyTransform() { findEntity().ifPresent(e-> { var l=data.location.toLocation(); if(l!=null) e.teleport(l); }); }
    @Override protected void applyCommon() { findEntity().ifPresent(e-> commonDisplayTweaks((org.bukkit.entity.Display)e)); }
    @Override protected void applyVisibility() {}

    public void setItem(ItemStack stack) {
        data.itemStackBase64 = encodeItem(stack);
        findEntity().ifPresent(e -> ((ItemDisplay)e).setItemStack(stack));
    }

    private static String encodeItem(ItemStack stack) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(bos)) {
            oos.writeObject(stack);
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) { return ""; }
    }
    private static ItemStack decodeItem(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getDecoder().decode(b64));
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bis)) {
            return (ItemStack) ois.readObject();
        } catch (Throwable t) { return null; }
    }
}
