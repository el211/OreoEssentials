package fr.elias.oreoEssentials.holograms;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Transformation;

public final class BlockOreoHologram extends OreoHologram {

    public BlockOreoHologram(String name, OreoHologramData data) { super(name, data); }

    @Override public void spawnIfMissing() {
        var loc = data.location.toLocation(); if (loc == null) return;
        if (findEntity().isPresent()) return;
        BlockDisplay bd = (BlockDisplay) loc.getWorld().spawnEntity(loc, EntityType.BLOCK_DISPLAY);
        entityId = bd.getUniqueId();
        applyTransform(); applyCommon(); applyVisibility();
        Material m = data.blockType == null || data.blockType.isEmpty() ? Material.STONE : Material.matchMaterial(data.blockType);
        if (m == null) m = Material.STONE;
        bd.setBlock(m.createBlockData());
    }

    @Override public void despawn() { findEntity().ifPresent(e -> e.remove()); entityId = null; }
    @Override public Location currentLocation() { var e=findEntity(); return e.map(Entity::getLocation).orElseGet(()->data.location.toLocation()); }
    @Override protected void applyTransform() { findEntity().ifPresent(e-> { var l=data.location.toLocation(); if(l!=null) e.teleport(l); }); }
    @Override protected void applyCommon() { findEntity().ifPresent(e-> commonDisplayTweaks((org.bukkit.entity.Display)e)); }
    @Override protected void applyVisibility() {}

    public void setBlockType(String t) {
        data.blockType = t;
        findEntity().ifPresent(e -> {
            var bd = (BlockDisplay) e;
            var m = Material.matchMaterial(t);
            if (m != null) bd.setBlock(m.createBlockData());
        });
    }
}
