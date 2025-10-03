package fr.elias.oreoEssentials.kits;


import org.bukkit.inventory.ItemStack;
import java.util.List;


public class Kit {
    private final String id;
    private final String displayName;
    private final ItemStack icon;
    private final List<ItemStack> items;
    private final long cooldownSeconds;
    private final Integer slot; // nullable


    public Kit(String id, String displayName, ItemStack icon, List<ItemStack> items, long cooldownSeconds, Integer slot) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.items = items;
        this.cooldownSeconds = cooldownSeconds;
        this.slot = slot;
    }


    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public ItemStack getIcon() { return icon; }
    public List<ItemStack> getItems() { return items; }
    public long getCooldownSeconds() { return cooldownSeconds; }
    public Integer getSlot() { return slot; }
}