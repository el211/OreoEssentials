// File: src/main/java/fr/elias/oreoEssentials/config/LegacySettingsMigrator.java
package fr.elias.oreoEssentials.config;

import fr.elias.oreoEssentials.OreoEssentials;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Incremental migration + merge of feature toggles into settings.yml.
 *
 * Behaviour:
 *  - ALWAYS runs on startup (safe & idempotent).
 *  - Ensures settings.yml exists; if not, creates it from jar defaults.
 *  - Merges missing keys from the default settings.yml in the jar.
 *  - Copies legacy booleans from:
 *      - old config.yml (flat + nested)
 *      - module configs: rtp.yml, scoreboard.yml, bossbar.yml, clearlag.yml, etc.
 *    -> into settings.yml under features.* ONLY if the target key is not set yet.
 *  - NEVER overwrites an existing settings.yml value.
 */
public final class LegacySettingsMigrator {

    private LegacySettingsMigrator() {
    }

    public static void migrate(OreoEssentials plugin) {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File settingsFile = new File(dataFolder, "settings.yml");

        // 1) Ensure settings.yml exists (try from jar, else empty)
        if (!settingsFile.exists()) {
            try {
                plugin.saveResource("settings.yml", false);
            } catch (IllegalArgumentException ignored) {
                try {
                    if (!settingsFile.exists()) {
                        settingsFile.createNewFile();
                    }
                } catch (IOException e) {
                    plugin.getLogger().warning("[Settings] Could not create settings.yml: " + e.getMessage());
                    return;
                }
            }
        }

        // 2) Load current settings.yml
        FileConfiguration settingsCfg = YamlConfiguration.loadConfiguration(settingsFile);
        boolean changed = false;

        // 3) Merge defaults from the jar (fill in missing keys only)
        try (InputStreamReader reader = new InputStreamReader(
                plugin.getResource("settings.yml"),
                StandardCharsets.UTF_8
        )) {
            if (reader != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
                for (String key : defaults.getKeys(true)) {
                    if (!settingsCfg.isSet(key)) {
                        settingsCfg.set(key, defaults.get(key));
                        changed = true;
                    }
                }
            }
        } catch (Exception ignored) {
            // If we can't read defaults, we still try to migrate from legacy sources.
        }

        // 4) Load main old config.yml
        File configFile = new File(dataFolder, "config.yml");
        FileConfiguration mainCfg = YamlConfiguration.loadConfiguration(configFile);

        // --------------------------------------------------------------------
        // 5) FROM OLD config.yml (top-level / old layout)
        //     Only copy if target key in settings.yml is missing.
        // --------------------------------------------------------------------

        // bossbar.enabled -> features.bossbar.enabled
        changed |= copyBoolIfMissing(mainCfg, "bossbar.enabled",
                settingsCfg, "features.bossbar.enabled");

        // chat.enabled -> features.chat.enabled
        changed |= copyBoolIfMissing(mainCfg, "chat.enabled",
                settingsCfg, "features.chat.enabled");

        // chat.discord-bridge -> features.chat.discord-bridge
        changed |= copyBoolIfMissing(mainCfg, "chat.discord-bridge",
                settingsCfg, "features.chat.discord-bridge");

        // economy.enabled -> features.economy.enabled
        changed |= copyBoolIfMissing(mainCfg, "economy.enabled",
                settingsCfg, "features.economy.enabled");

        // jumppads.enabled -> features.jumppads.enabled
        changed |= copyBoolIfMissing(mainCfg, "jumppads.enabled",
                settingsCfg, "features.jumppads.enabled");

        // kits.enabled -> features.kits.enabled
        changed |= copyBoolIfMissing(mainCfg, "kits.enabled",
                settingsCfg, "features.kits.enabled");

        // kits.register-commands -> features.kits.register-commands
        changed |= copyBoolIfMissing(mainCfg, "kits.register-commands",
                settingsCfg, "features.kits.register-commands");

        // mobs.enabled -> features.mobs.enabled
        changed |= copyBoolIfMissing(mainCfg, "mobs.enabled",
                settingsCfg, "features.mobs.enabled");

        // mobs.healthbar -> features.mobs.healthbar
        changed |= copyBoolIfMissing(mainCfg, "mobs.healthbar",
                settingsCfg, "features.mobs.healthbar");

        // clearlag.enabled -> features.clearlag.enabled
        changed |= copyBoolIfMissing(mainCfg, "clearlag.enabled",
                settingsCfg, "features.clearlag.enabled");

        // oreoholograms.enabled -> features.oreoholograms.enabled
        changed |= copyBoolIfMissing(mainCfg, "oreoholograms.enabled",
                settingsCfg, "features.oreoholograms.enabled");

        // playervaults.enabled -> features.playervaults.enabled
        changed |= copyBoolIfMissing(mainCfg, "playervaults.enabled",
                settingsCfg, "features.playervaults.enabled");

        // playtime-rewards.enabled -> features.playtime-rewards.enabled
        changed |= copyBoolIfMissing(mainCfg, "playtime-rewards.enabled",
                settingsCfg, "features.playtime-rewards.enabled");

        // portals.enabled -> features.portals.enabled
        changed |= copyBoolIfMissing(mainCfg, "portals.enabled",
                settingsCfg, "features.portals.enabled");

        // rtp.enabled -> features.rtp.enabled
        changed |= copyBoolIfMissing(mainCfg, "rtp.enabled",
                settingsCfg, "features.rtp.enabled");

        // scoreboard.enabled -> features.scoreboard.enabled
        changed |= copyBoolIfMissing(mainCfg, "scoreboard.enabled",
                settingsCfg, "features.scoreboard.enabled");

        // sit.enabled -> features.sit.enabled
        changed |= copyBoolIfMissing(mainCfg, "sit.enabled",
                settingsCfg, "features.sit.enabled");

        // trade.enabled -> features.trade.enabled
        changed |= copyBoolIfMissing(mainCfg, "trade.enabled",
                settingsCfg, "features.trade.enabled");

        // trade.cross-server -> features.trade.cross-server
        changed |= copyBoolIfMissing(mainCfg, "trade.cross-server",
                settingsCfg, "features.trade.cross-server");

        // discord-moderation.enabled -> features.discord-moderation.enabled
        changed |= copyBoolIfMissing(mainCfg, "discord-moderation.enabled",
                settingsCfg, "features.discord-moderation.enabled");

        // cross-server.* (homes / warps / spawn / economy / enderchest)
        changed |= copyBoolIfMissing(mainCfg, "cross-server.homes",
                settingsCfg, "features.cross-server.homes");
        changed |= copyBoolIfMissing(mainCfg, "cross-server.warps",
                settingsCfg, "features.cross-server.warps");
        changed |= copyBoolIfMissing(mainCfg, "cross-server.spawn",
                settingsCfg, "features.cross-server.spawn");
        changed |= copyBoolIfMissing(mainCfg, "cross-server.economy",
                settingsCfg, "features.cross-server.economy");
        changed |= copyBoolIfMissing(mainCfg, "cross-server.enderchest",
                settingsCfg, "features.cross-server.enderchest");

        // tab.enabled -> features.tab.enabled
        changed |= copyBoolIfMissing(mainCfg, "tab.enabled",
                settingsCfg, "features.tab.enabled");

        // --------------------------------------------------------------------
        // 6) FROM MODULE FILES (rtp.yml, scoreboard.yml, etc.)
        // --------------------------------------------------------------------

        // RTP
        changed |= copyBoolFromFileIfMissing(plugin, "rtp.yml", "rtp.enabled",
                settingsCfg, "features.rtp.enabled");

        // Scoreboard
        changed |= copyBoolFromFileIfMissing(plugin, "scoreboard.yml", "scoreboard.enabled",
                settingsCfg, "features.scoreboard.enabled");

        // BossBar
        changed |= copyBoolFromFileIfMissing(plugin, "bossbar.yml", "bossbar.enabled",
                settingsCfg, "features.bossbar.enabled");

        // ClearLag
        changed |= copyBoolFromFileIfMissing(plugin, "clearlag.yml", "clearlag.enabled",
                settingsCfg, "features.clearlag.enabled");

        // Playtime rewards
        changed |= copyBoolFromFileIfMissing(plugin, "playtime-rewards.yml", "playtime-rewards.enabled",
                settingsCfg, "features.playtime-rewards.enabled");
        changed |= copyBoolFromFileIfMissing(plugin, "playtime.yml", "playtime.enabled",
                settingsCfg, "features.playtime-rewards.enabled");

        // OreoHolograms
        changed |= copyBoolFromFileIfMissing(plugin, "oreoholograms.yml", "oreoholograms.enabled",
                settingsCfg, "features.oreoholograms.enabled");

        // Sit
        changed |= copyBoolFromFileIfMissing(plugin, "sit.yml", "sit.enabled",
                settingsCfg, "features.sit.enabled");

        // Trade
        changed |= copyBoolFromFileIfMissing(plugin, "trade.yml", "trade.enabled",
                settingsCfg, "features.trade.enabled");
        changed |= copyBoolFromFileIfMissing(plugin, "trade.yml", "trade.cross-server",
                settingsCfg, "features.trade.cross-server");

        // Portals
        changed |= copyBoolFromFileIfMissing(plugin, "portals.yml", "portals.enabled",
                settingsCfg, "features.portals.enabled");

        // JumpPads (typo-safe: jumpads.yml / jumppads.yml)
        changed |= copyBoolFromFileIfMissing(plugin, "jumpads.yml", "jumppads.enabled",
                settingsCfg, "features.jumppads.enabled");
        changed |= copyBoolFromFileIfMissing(plugin, "jumppads.yml", "jumppads.enabled",
                settingsCfg, "features.jumppads.enabled");

        // PlayerVaults
        changed |= copyBoolFromFileIfMissing(plugin, "playervaults.yml", "playervaults.enabled",
                settingsCfg, "features.playervaults.enabled");
        changed |= copyBoolFromFileIfMissing(plugin, "vaults.yml", "playervaults.enabled",
                settingsCfg, "features.playervaults.enabled");

        // Kits
        changed |= copyBoolFromFileIfMissing(plugin, "kits.yml", "kits.enabled",
                settingsCfg, "features.kits.enabled");
        changed |= copyBoolFromFileIfMissing(plugin, "kits.yml", "kits.register-commands",
                settingsCfg, "features.kits.register-commands");

        // Mobs / healthbar
        changed |= copyBoolFromFileIfMissing(plugin, "mobs.yml", "mobs.enabled",
                settingsCfg, "features.mobs.enabled");
        changed |= copyBoolFromFileIfMissing(plugin, "mobs.yml", "mobs.healthbar",
                settingsCfg, "features.mobs.healthbar");

        // Chat + Discord bridge (if you had chat.yml)
        changed |= copyBoolFromFileIfMissing(plugin, "chat.yml", "chat.enabled",
                settingsCfg, "features.chat.enabled");
        changed |= copyBoolFromFileIfMissing(plugin, "chat.yml", "chat.discord-bridge",
                settingsCfg, "features.chat.discord-bridge");

        // Discord moderation
        changed |= copyBoolFromFileIfMissing(plugin, "discord.yml", "discord-moderation.enabled",
                settingsCfg, "features.discord-moderation.enabled");

        // --- Cross-server toggles from legacy flat keys in config.yml ---
        // Old keys:
        //   crossserverhomes
        //   crossserverwarps
        //   crossserverspawn
        //   crossservereconomy
        //   crossserverec       -> enderchest
        //   crossserverinv      -> inventory sync master toggle
        changed |= copyBoolFromRootIfMissing(plugin, "crossserverhomes",
                settingsCfg, "features.cross-server.homes");
        changed |= copyBoolFromRootIfMissing(plugin, "crossserverwarps",
                settingsCfg, "features.cross-server.warps");
        changed |= copyBoolFromRootIfMissing(plugin, "crossserverspawn",
                settingsCfg, "features.cross-server.spawn");
        changed |= copyBoolFromRootIfMissing(plugin, "crossservereconomy",
                settingsCfg, "features.cross-server.economy");
        changed |= copyBoolFromRootIfMissing(plugin, "crossserverec",
                settingsCfg, "features.cross-server.enderchest");
        changed |= copyBoolFromRootIfMissing(plugin, "crossserverinv",
                settingsCfg, "features.cross-server.inventory");

        // Still also support a hypothetical cross-server.yml if someone added one manually
        changed |= copyBoolFromFileIfMissing(plugin, "cross-server.yml", "homes",
                settingsCfg, "features.cross-server.homes");
        changed |= copyBoolFromFileIfMissing(plugin, "cross-server.yml", "warps",
                settingsCfg, "features.cross-server.warps");
        changed |= copyBoolFromFileIfMissing(plugin, "cross-server.yml", "spawn",
                settingsCfg, "features.cross-server.spawn");
        changed |= copyBoolFromFileIfMissing(plugin, "cross-server.yml", "economy",
                settingsCfg, "features.cross-server.economy");
        changed |= copyBoolFromFileIfMissing(plugin, "cross-server.yml", "enderchest",
                settingsCfg, "features.cross-server.enderchest");

        // Tab module file
        changed |= copyBoolFromFileIfMissing(plugin, "tab.yml", "tab.enabled",
                settingsCfg, "features.tab.enabled");

        // --------------------------------------------------------------------
        // 7) SAVE IF ANYTHING WAS MERGED / MIGRATED
        // --------------------------------------------------------------------
        if (changed) {
            try {
                settingsCfg.save(settingsFile);
                plugin.getLogger().info("[Settings] settings.yml updated (defaults merged + legacy toggles migrated).");
            } catch (IOException e) {
                plugin.getLogger().warning("[Settings] Failed to save settings.yml: " + e.getMessage());
            }
        } else {
            plugin.getLogger().info("[Settings] No changes needed; settings.yml already up to date.");
        }
    }

    /* ------------------------------------------------------------------ */
    /* Helper methods: never override existing target values              */
    /* ------------------------------------------------------------------ */

    private static boolean copyBoolIfMissing(ConfigurationSection from, String fromPath,
                                             FileConfiguration to, String toPath) {
        if (from == null || !from.isSet(fromPath)) {
            return false;
        }
        if (to.isSet(toPath)) {
            // User already has a value in settings.yml -> do not touch
            return false;
        }
        boolean value = from.getBoolean(fromPath);
        to.set(toPath, value);
        return true;
    }

    private static boolean copyBoolFromRootIfMissing(OreoEssentials plugin,
                                                     String oldPath,
                                                     FileConfiguration targetCfg,
                                                     String newPath) {
        FileConfiguration root = plugin.getConfig(); // old config.yml
        if (!root.isSet(oldPath)) return false;
        if (targetCfg.isSet(newPath)) return false;

        boolean val = root.getBoolean(oldPath);
        targetCfg.set(newPath, val);
        return true;
    }

    private static boolean copyBoolFromFileIfMissing(OreoEssentials plugin,
                                                     String fileName,
                                                     String fromPath,
                                                     FileConfiguration to,
                                                     String toPath) {
        File f = new File(plugin.getDataFolder(), fileName);
        if (!f.exists()) return false;

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        return copyBoolIfMissing(cfg, fromPath, to, toPath);
    }
}
