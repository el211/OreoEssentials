package fr.elias.oreoEssentials.holograms;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;

public final class TextOreoHologram extends OreoHologram {

    public TextOreoHologram(String name, OreoHologramData data) { super(name, data); }

    @Override public void spawnIfMissing() {
        var loc = data.location.toLocation();
        if (loc == null) return;
        if (findEntity().isPresent()) return;

        // 👇 ensure something visible is rendered immediately
        if (data.lines == null) data.lines = new java.util.ArrayList<>();
        if (data.lines.isEmpty()) {
            data.lines.add("§fEdit this line with §7/ohologram edit " + name);
        }

        TextDisplay td = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        entityId = td.getUniqueId();
        applyTransform();
        applyCommon();
        applyVisibility();
        td.setText(joinLines(data.lines));
        textTweaks(td);
    }


    @Override public void despawn() {
        findEntity().ifPresent(e -> { e.remove(); });
        entityId = null;
    }

    @Override public Location currentLocation() {
        var e = findEntity(); if (e.isPresent()) return e.get().getLocation();
        return data.location.toLocation();
    }

    @Override protected void applyTransform() {
        findEntity().ifPresent(e -> {
            var l = data.location.toLocation();
            if (l != null) e.teleport(l);
        });
    }

    @Override protected void applyCommon() {
        findEntity().ifPresent(e -> commonDisplayTweaks((org.bukkit.entity.Display) e));
    }

    @Override protected void applyVisibility() {
        // Display entities don’t have per-player visibility; we control via rules in command layer (simple gate).
        // For now we keep entity global; view rules enforced by “visibilityDistance” and admin flows.
        // If needed you can use packets or per-player entity tracking (advanced).
    }

    @Override protected void onTimedUpdate() {
        // placeholder refresh hook (wire PlaceholderAPI here if desired)
        findEntity().ifPresent(e -> {
            TextDisplay td = (TextDisplay) e;
            td.setText(joinLines(data.lines));
        });
    }

    // text API
    public void setLine(int index, String text) {
        while (data.lines.size() < index) data.lines.add("");
        if (index <= 0 || index > data.lines.size()) throw new IllegalArgumentException("Invalid line");
        data.lines.set(index-1, text);
        findEntity().ifPresent(e -> ((TextDisplay)e).setText(joinLines(data.lines)));
    }
    public void addLine(String text) {
        data.lines.add(text);
        findEntity().ifPresent(e -> ((TextDisplay)e).setText(joinLines(data.lines)));
    }
    public void insertBefore(int line, String text) {
        int idx = Math.max(0, Math.min(line-1, data.lines.size()));
        data.lines.add(idx, text);
        findEntity().ifPresent(e -> ((TextDisplay)e).setText(joinLines(data.lines)));
    }
    public void insertAfter(int line, String text) {
        int idx = Math.max(0, Math.min(line, data.lines.size()));
        data.lines.add(idx, text);
        findEntity().ifPresent(e -> ((TextDisplay)e).setText(joinLines(data.lines)));
    }
    public void removeLine(int line) {
        int idx = line-1;
        if (idx < 0 || idx >= data.lines.size()) throw new IllegalArgumentException("Invalid line");
        data.lines.remove(idx);
        findEntity().ifPresent(e -> ((TextDisplay)e).setText(joinLines(data.lines)));
    }
    public void setBackground(String color) { data.backgroundColor = color; findEntity().ifPresent(e-> textTweaks((TextDisplay)e)); }
    public void setTextShadow(boolean shadow) { data.textShadow = shadow; findEntity().ifPresent(e-> textTweaks((TextDisplay)e)); }
    public void setAlignment(String align) { data.textAlign = align; findEntity().ifPresent(e-> textTweaks((TextDisplay)e)); }
    public void setUpdateIntervalTicks(long ticks) { data.updateIntervalTicks = Math.max(0, ticks); }


}
