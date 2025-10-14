package fr.elias.oreoEssentials.offineplayers;

import org.bukkit.inventory.ItemStack;

public final class InvSnapshot {
    public ItemStack[] contents;   // size from PlayerInventory#getContents (usually 41)
    public ItemStack[] armor;      // 4
    public ItemStack   offhand;    // 1
}

