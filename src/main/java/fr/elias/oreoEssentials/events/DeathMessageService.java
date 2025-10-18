// File: src/main/java/fr/elias/oreoEssentials/events/DeathMessageService.java
package fr.elias.oreoEssentials.events;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class DeathMessageService {
    private final File file;
    private YamlConfiguration yml;

    public DeathMessageService(File dataFolder) {
        this.file = new File(dataFolder, "death-messages.yml");
        if (!file.exists()) writeSkeleton(); // you can paste your full config once and remove this writer
        reload();
    }

    public void reload() { this.yml = YamlConfiguration.loadConfiguration(file); }

    public String prefix() { return yml.getString("Prefix", ""); }

    public List<String> playerHover() { return yml.getStringList("PlayerHover"); }
    public List<String> killerHover() { return yml.getStringList("KillerHover"); }

    // ADD this overload (keep your original buildMessage(...) as-is)
    public String buildMessage(
            Player dead,
            Player killer,
            EntityType mobKiller,
            EntityDamageEvent.DamageCause cause,
            ItemStack itemUsed,
            String projectileType,
            String mythicId,            // e.g., "AncientOverlord"
            String mythicDisplayName    // colored display, may be null
    ) {
        Map<String, String> vars = new HashMap<>();
        vars.put("[playerDisplayName]", dead.getDisplayName());
        vars.put("[playerName]", dead.getName());
        vars.put("[killerName]", killer != null ? killer.getName() : "");
        vars.put("[type]", projectileType != null ? projectileType : "");
        vars.put("[item]", itemUsed != null && itemUsed.getType() != null ? pretty(itemUsed.getType().name()) : "");
        vars.put("[mobMythicId]", mythicId == null ? "" : mythicId);
        vars.put("[mobMythicName]", mythicDisplayName == null ? "" : mythicDisplayName);

        // Source display preference: player > mythic display > mob pretty > "Unknown"
        String sourceDisplay = killer != null ? killer.getDisplayName() :
                (mythicDisplayName != null && !mythicDisplayName.isEmpty() ? mythicDisplayName :
                        (mobKiller != null ? pretty(mobKiller.name()) : "Unknown"));
        vars.put("[sourceDisplayName]", sourceDisplay);

        String line = null;

        // 1) Player killer
        if (killer != null) {
            if (itemUsed != null && itemUsed.getType() != null) line = pick(yml.getStringList("Player.Item"));
            if (line == null && projectileType != null)        line = pick(yml.getStringList("Player.Projectile"));
            if (line == null)                                  line = pick(yml.getStringList("Player.General"));
        }
        // 2) Mythic mob killer (highest priority over vanilla mob section)
        else if (mythicId != null && !mythicId.isEmpty()) {
            String base = "Mythic." + mythicId; // exact internal ID
            String li = null;
            if (itemUsed != null && itemUsed.getType() != null) li = pick(yml.getStringList(base + ".Item"));
            if (li == null) li = pick(yml.getStringList(base + ".General"));
            line = li;
        }
        // 3) Vanilla mob killer
        else if (mobKiller != null) {
            String base = "Mob." + mobKiller.name().toLowerCase(Locale.ROOT);
            String li = null;
            if (itemUsed != null && itemUsed.getType() != null) li = pick(yml.getStringList(base + ".Item"));
            if (li == null) li = pick(yml.getStringList(base + ".General"));
            if (li == null) li = pick(yml.getStringList("Mob.primed_tnt.General"));
            line = li;
        }
        // 4) Environment
        else if (cause != null) {
            String base = "Custom." + causePath(cause);
            List<String> general = yml.getStringList(base + ".General");
            if (general.isEmpty() && cause == EntityDamageEvent.DamageCause.FALL) {
                general = yml.getStringList("Custom.Fall.General");
                if (general.isEmpty()) general = yml.getStringList("Custom.Fall.Low.General");
            }
            line = pick(general);
        }

        if (line == null) line = "&2[playerDisplayName] &7died";
        for (var e : vars.entrySet()) line = line.replace(e.getKey(), e.getValue());
        return colorize(prefix() + " " + line);
    }

    // Keep your old method but make it delegate to the new one
    public String buildMessage(Player dead, Player killer, EntityType mobKiller, EntityDamageEvent.DamageCause cause, ItemStack itemUsed, String projectileType) {
        return buildMessage(dead, killer, mobKiller, cause, itemUsed, projectileType, null, null);
    }

    private String pick(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        return list.get(new Random().nextInt(list.size()));
    }

    private String colorize(String s) { return s.replace("&", "§"); }

    private String pretty(String key) {
        String n = key.toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] p = n.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : p) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private String causePath(EntityDamageEvent.DamageCause c) {
        return switch (c) {
            case LAVA -> "Block.Lava";
            case FIRE, FIRE_TICK -> "Custom.Fire";
            case LIGHTNING -> "Custom.Lightning";
            case SUFFOCATION -> "Custom.Suffocation";
            case DROWNING -> "Custom.Drowning";
            case STARVATION -> "Custom.Starvation";
            case VOID -> "Custom.Void";
            case FREEZE -> "Custom.Freeze";
            case FALL -> "Custom.Fall";
            case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> "Custom.Block_explosion";
            case WITHER -> "Custom.Wither";
            case DRAGON_BREATH -> "Custom.EndCrystal"; // rough mapping
            default -> "Custom.Custom";
        };
    }

    private void writeSkeleton() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("Prefix", "💀");
        y.set("PlayerHover", List.of(""));
        y.set("KillerHover", List.of(""));
        y.set("Player.General", List.of("&2[playerDisplayName] &7was killed by &2[sourceDisplayName]"));
        y.set("Custom.Fall.Low.General", List.of("&2[playerDisplayName] &7hit the ground too hard"));
        try { y.save(file); } catch (IOException ignored) {}
        Bukkit.getLogger().info("[OreoEssentials] Wrote skeleton death-messages.yml (replace with your full config).");
    }
}
