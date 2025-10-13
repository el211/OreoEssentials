package fr.elias.oreoEssentials.playervaults;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public final class PlayerVaultsConfig {

    private final boolean enabled;
    private final String storage;                // auto|yaml|mongodb
    private final String collection;

    private final int maxVaults;                 // 1..54

    // SLOTS mode (not rows)
    private final int slotsCap;                  // 1..54
    private final int defaultSlots;              // 1..slotsCap

    private final String denyMessage;

    private final Sound openSound;
    private final Sound denySound;

    // menu text
    private final String menuTitle;
    private final String menuItemUnlockedName;
    private final String menuItemLockedName;
    private final String menuItemUnlockedLore;
    private final String menuItemLockedLore;

    // vault inventory title (<id>, <rows> supported; rows = ceil(slots/9))
    private final String vaultTitle;

    // how many vaults are unlocked for each rank (rank -> count)
    private final Map<String, Integer> accessGlobal;

    // per-vault SLOTS by rank (vaultId -> (rank/default -> slots))
    private final Map<Integer, Map<String, Integer>> slotsPerVault;

    public PlayerVaultsConfig(OreoEssentials plugin) {
        ConfigurationSection cs = plugin.getConfig().getConfigurationSection("playervaults");
        if (cs == null) cs = plugin.getConfig().createSection("playervaults");

        enabled    = cs.getBoolean("enabled", true);
        storage    = cs.getString("storage", "auto");
        collection = cs.getString("collection", "oreo_playervaults");

        maxVaults   = clamp(cs.getInt("max-vaults", 36), 1, 54);
        slotsCap     = clamp(cs.getInt("slots-cap", 54), 1, 54);
        defaultSlots = clamp(cs.getInt("default-slots", 9), 1, slotsCap);

        denyMessage = cs.getString("deny-message", "&cYou don't have permission to access vault &f#%id%&c.");

        // sounds
        ConfigurationSection snd = cs.getConfigurationSection("sounds");
        openSound = safeSound(snd != null ? snd.getString("open", "UI_BUTTON_CLICK") : "UI_BUTTON_CLICK",
                Sound.UI_BUTTON_CLICK);
        denySound = safeSound(snd != null ? snd.getString("deny", "ENTITY_VILLAGER_NO") : "ENTITY_VILLAGER_NO",
                Sound.ENTITY_VILLAGER_NO);

        // menu
        ConfigurationSection menu = cs.getConfigurationSection("menu");
        menuTitle            = opt(menu, "title", "&8Player Vaults");
        menuItemUnlockedName = opt(menu, "item-unlocked-name", "&aVault &f#<id>");
        menuItemLockedName   = opt(menu, "item-locked-name", "&cVault &f#<id> &7(locked)");
        menuItemUnlockedLore = opt(menu, "item-unlocked-lore", "&7Click to open");
        menuItemLockedLore   = opt(menu, "item-locked-lore", "&7You don't have access");

        // per-vault inventory title
        vaultTitle = cs.getString("vault-title", "&8Vault &f#<id> &7(<rows>x9)");

        // vaults-per-rank
        Map<String, Integer> access = new HashMap<>();
        Map<Integer, Map<String, Integer>> perVault = new HashMap<>();

        ConfigurationSection vpr = cs.getConfigurationSection("vaults-per-rank");
        if (vpr != null) {
            // global unlocked counts
            ConfigurationSection global = vpr.getConfigurationSection("global");
            if (global != null) {
                for (String k : global.getKeys(false)) {
                    int val = global.getInt(k, -1);
                    if (val >= 0) access.put(k.toLowerCase(Locale.ROOT), clamp(val, 0, maxVaults));
                }
            }
            // per-vault slots by rank
            ConfigurationSection pv = vpr.getConfigurationSection("per-vault");
            if (pv != null) {
                for (String vk : pv.getKeys(false)) {
                    int id;
                    try { id = Integer.parseInt(vk); } catch (NumberFormatException e) { continue; }
                    ConfigurationSection rSec = pv.getConfigurationSection(vk);
                    if (rSec == null) continue;
                    Map<String, Integer> ranks = new HashMap<>();
                    for (String rk : rSec.getKeys(false)) {
                        int slots = rSec.getInt(rk, -1);
                        if (slots > 0) ranks.put(rk.toLowerCase(Locale.ROOT), clamp(slots, 1, slotsCap));
                    }
                    if (!ranks.isEmpty()) perVault.put(id, Collections.unmodifiableMap(ranks));
                }
            }
        }

        accessGlobal  = Collections.unmodifiableMap(access);
        slotsPerVault = Collections.unmodifiableMap(perVault);
    }

    /* ------------ getters (names match your service) ------------ */

    public boolean enabled() { return enabled; }
    public String storage() { return storage; }
    public String collection() { return collection; }

    public int maxVaults() { return maxVaults; }

    public int slotsCap() { return slotsCap; }
    /** Alias so code that calls slotCap() compiles without edits. */
    public int slotCap() { return slotsCap; }

    public int defaultSlots() { return defaultSlots; }

    public String denyMessage() { return denyMessage; }
    public Sound openSound() { return openSound; }
    public Sound denySound() { return denySound; }

    public String menuTitle() { return menuTitle; }
    public String menuItemUnlockedName() { return menuItemUnlockedName; }
    public String menuItemLockedName() { return menuItemLockedName; }
    public String menuItemUnlockedLore() { return menuItemUnlockedLore; }
    public String menuItemLockedLore() { return menuItemLockedLore; }

    public String vaultTitle() { return vaultTitle; }

    /** How many vault IDs are auto-unlocked for this rank (fallback to "default" or 0). */
    public int unlockedCountFor(String rankOrNull) {
        if (rankOrNull != null) {
            Integer v = accessGlobal.get(rankOrNull.toLowerCase(Locale.ROOT));
            if (v != null) return clamp(v, 0, maxVaults);
        }
        return clamp(accessGlobal.getOrDefault("default", 0), 0, maxVaults);
    }

    /** Slots rule for a specific vault id and rank. Checks rank first, then "default". Returns -1 if none. */
    public int slotsFromConfig(int vaultId, String rankOrNull) {
        Map<String, Integer> ranks = slotsPerVault.get(vaultId);
        if (ranks == null) return -1;
        if (rankOrNull != null) {
            Integer v = ranks.get(rankOrNull.toLowerCase(Locale.ROOT));
            if (v != null) return clamp(v, 1, slotsCap);
        }
        Integer def = ranks.get("default");
        return def != null ? clamp(def, 1, slotsCap) : -1;
    }

    /* ------------ helpers ------------ */

    private static String opt(ConfigurationSection sec, String key, String def) {
        return (sec != null && sec.isString(key)) ? sec.getString(key, def) : def;
    }

    private static Sound safeSound(String name, Sound fallback) {
        try { return Sound.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (Throwable t) { return fallback; }
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
