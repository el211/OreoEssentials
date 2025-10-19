package fr.elias.oreoEssentials.playtime;

import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

public final class RewardEntry {
    public final String id;
    public final String displayName;
    public final boolean autoClaim;
    public final List<String> description;

    // Triggering
    public final Long payFor;      // seconds (one-time)
    public final Long payEvery;    // seconds (repeating)

    // Behaviour
    public final boolean stackRewards;        // allow catching up missed cycles
    public final boolean requiresPermission;  // require oreo.prewards.<id>

    // Actions
    public final List<String> commands;

    // GUI skin
    public final Integer slot;               // 0..(rows*9-1), nullable = auto flow
    public final Material iconMaterial;      // defaults to PAPER
    public final Integer customModelData;    // nullable
    public final String iconName;            // nullable
    public final List<String> iconLore;      // nullable

    public RewardEntry(
            String id,
            String displayName,
            boolean autoClaim,
            List<String> description,
            Long payFor,
            Long payEvery,
            boolean stackRewards,
            boolean requiresPermission,
            List<String> commands,
            Integer slot,
            Material iconMaterial,
            Integer customModelData,
            String iconName,
            List<String> iconLore
    ) {
        this.id = id;
        this.displayName = displayName;
        this.autoClaim = autoClaim;
        this.description = (description != null) ? description : Collections.emptyList();

        this.payFor = payFor;
        this.payEvery = payEvery;

        this.stackRewards = stackRewards;
        this.requiresPermission = requiresPermission;

        this.commands = (commands != null) ? commands : Collections.emptyList();

        this.slot = slot;
        this.iconMaterial = (iconMaterial != null) ? iconMaterial : Material.PAPER;
        this.customModelData = customModelData;
        this.iconName = iconName;
        this.iconLore = (iconLore != null) ? iconLore : Collections.emptyList();
    }

    public boolean isOneTime() {
        return payFor != null && payFor > 0;
    }

    public boolean isRepeating() {
        return payEvery != null && payEvery > 0;
    }
}
