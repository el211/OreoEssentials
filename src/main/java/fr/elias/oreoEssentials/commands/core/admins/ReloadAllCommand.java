package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Method;
import java.util.List;

public final class ReloadAllCommand implements OreoCommand {

    @Override public String name() { return "oereload"; }
    @Override public List<String> aliases() { return List.of("oereloadall", "oer"); }
    @Override public String permission() { return "oreo.reload"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        final OreoEssentials plugin = OreoEssentials.get();
        final long start = System.currentTimeMillis();

        int ok = 0, skip = 0;

        // 1) Main config.yml
        try {
            plugin.reloadConfig();
            sender.sendMessage(col("&a✔ Reloaded &fconfig.yml"));
            ok++;
        } catch (Throwable t) {
            sender.sendMessage(col("&c✘ Failed reloading config.yml: &7" + t.getMessage()));
        }

        // 2) lang.yml
        try {
            Lang.init(plugin);
            sender.sendMessage(col("&a✔ Reloaded &flang.yml"));
            ok++;
        } catch (Throwable t) {
            sender.sendMessage(col("&e• Skipped lang.yml: &7" + t.getMessage()));
            skip++;
        }

        // 3) Chat (chat-format.yml etc.) via your Afelius command
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "afelius reload all");
            sender.sendMessage(col("&a✔ Reloaded &fchat-format.yml &7(via /afelius reload all)"));
            ok++;
        } catch (Throwable t) {
            sender.sendMessage(col("&e• Skipped chat-format.yml: &7" + t.getMessage()));
            skip++;
        }

        // 4) Tab list
        try {
            Object tab = plugin.getTabListManager();
            if (tab != null) {
                if (!callNoArgs(tab, "reload")) {
                    boolean a = callNoArgs(tab, "stop");
                    boolean b = callNoArgs(tab, "start");
                    if (!a && !b) throw new IllegalStateException("no reload/stop/start available");
                }
                sender.sendMessage(col("&a✔ Reloaded &ftab.yml"));
                ok++;
            } else {
                sender.sendMessage(col("&e• Skipped tab.yml (manager unavailable)"));
                skip++;
            }
        } catch (Throwable t) {
            sender.sendMessage(col("&e• Skipped tab.yml: &7" + t.getMessage()));
            skip++;
        }

        // 5) Kits (kits.yml)
        try {
            Object km = plugin.getKitsManager();
            if (km != null) {
                if (!callNoArgs(km, "reload") && !callNoArgs(km, "load")) {
                    throw new IllegalStateException("no reload/load available");
                }
                sender.sendMessage(col("&a✔ Reloaded &fkits.yml"));
                ok++;
            } else {
                sender.sendMessage(col("&e• Skipped kits.yml (manager unavailable)"));
                skip++;
            }
        } catch (Throwable t) {
            sender.sendMessage(col("&e• Skipped kits.yml: &7" + t.getMessage()));
            skip++;
        }

        // 6) RTP (rtp.yml)
        try {
            var rtpCfg = plugin.getRtpConfig();
            if (rtpCfg != null) {
                rtpCfg.reload();
                sender.sendMessage(col("&a✔ Reloaded &frtp.yml"));
                ok++;
            } else {
                sender.sendMessage(col("&e• Skipped rtp.yml (service unavailable)"));
                skip++;
            }
        } catch (Throwable t) {
            sender.sendMessage(col("&e• Skipped rtp.yml: &7" + t.getMessage()));
            skip++;
        }

        // 7) EnderChest config/service
        try {
            var ecService = plugin.getEnderChestService();
            if (ecService != null && (callNoArgs(ecService, "reload") || callNoArgs(ecService, "refresh"))) {
                sender.sendMessage(col("&a✔ Reloaded &fenderchest.yml"));
                ok++;
            } else {
                sender.sendMessage(col("&e• Skipped enderchest.yml (no reload hook)"));
                skip++;
            }
        } catch (Throwable ignored) {
            sender.sendMessage(col("&e• Skipped enderchest.yml (no reload hook)"));
            skip++;
        }

        // 8) BossBar
        try {
            var bb = plugin.getBossBarService();
            if (bb != null) {
                bb.reload();
                sender.sendMessage(col("&a✔ Reloaded &fbossbar"));
                ok++;
            } else {
                sender.sendMessage(col("&e• Skipped bossbar (service unavailable)"));
                skip++;
            }
        } catch (Throwable t) {
            sender.sendMessage(col("&c✘ Failed reloading bossbar: &7" + t.getMessage()));
        }

        // 9) Scoreboard
        try {
            if (plugin.getScoreboardService() != null) {
                plugin.getScoreboardService().reload();
                sender.sendMessage(col("&a✔ Reloaded &fscoreboard &7(config + live refresh)"));
                ok++;
            } else {
                sender.sendMessage(col("&e• Skipped scoreboard (service unavailable)"));
                skip++;
            }
        } catch (Throwable t) {
            sender.sendMessage(col("&c✘ Failed reloading scoreboard: &7" + t.getMessage()));
        }

        // 10) Discord integration (optional)
        try {
            var dm = plugin.getDiscordMod();
            if (dm != null && callNoArgs(dm, "reload")) {
                sender.sendMessage(col("&a✔ Reloaded &fdiscord integration"));
                ok++;
            } else {
                sender.sendMessage(col("&e• Skipped discord integration (no reload hook)"));
                skip++;
            }
        } catch (Throwable ignored) {
            sender.sendMessage(col("&e• Skipped discord integration (no reload hook)"));
            skip++;
        }
        // Health bars (mobs)
        try {
            // Re-register the listener based on fresh config
            var core = OreoEssentials.get(); // your plugin instance

            // Soft-hook UltimateChristmas again on reload
            fr.elias.ultimateChristmas.UltimateChristmas xmasHook = null;
            try {
                org.bukkit.plugin.Plugin maybe = core.getServer().getPluginManager().getPlugin("UltimateChristmas");
                if (maybe instanceof fr.elias.ultimateChristmas.UltimateChristmas uc && maybe.isEnabled()) {
                    xmasHook = uc;
                }
            } catch (Throwable ignored) {
                // UltimateChristmas not present, that's fine
            }

            // create new listener with BOTH args
            var hbl = new fr.elias.oreoEssentials.mobs.HealthBarListener(core, xmasHook);

            if (hbl.isEnabled()) {
                // register events with Bukkit
                Bukkit.getPluginManager().registerEvents(hbl, core);

                // reflect-set OreoEssentials.healthBarListener = hbl;
                var f = OreoEssentials.class.getDeclaredField("healthBarListener");
                f.setAccessible(true);
                f.set(core, hbl);

                sender.sendMessage(col("&a✔ Reloaded &fmob health bars"));
            } else {
                sender.sendMessage(col("&e• Mob health bars disabled by config"));

                // reflect-set OreoEssentials.healthBarListener = null;
                var f = OreoEssentials.class.getDeclaredField("healthBarListener");
                f.setAccessible(true);
                f.set(core, null);
            }

        } catch (Throwable t) {
            sender.sendMessage(col("&e• Skipped mob health bars: &7" + t.getMessage()));
        }


        // PlayerVaults
        try {
            var pv = plugin.getPlayervaultsService(); // make sure this getter name matches your plugin
            if (pv != null) {
                pv.reload(); // re-read config + rebind storage (Mongo/YAML)
                sender.sendMessage(col("&a✔ Reloaded &fplayervaults &7(" + pvStorageName(pv) + ")"));
                ok++;
            } else {
                sender.sendMessage(col("&e• Skipped playervaults (service unavailable)"));
                skip++;
            }
        } catch (Throwable t) {
            sender.sendMessage(col("&c✘ Failed reloading playervaults: &7" + t.getMessage()));
        }


        long took = System.currentTimeMillis() - start;
        sender.sendMessage(col("&7Reload complete: &a" + ok + " OK&7, &e" + skip + " skipped&7. (&f" + took + " ms&7)"));
        sender.sendMessage(col("&8(Some modules don’t support hot reload yet; a server restart may be required.)"));
        return true;
    }

    private static boolean callNoArgs(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            m.setAccessible(true);
            m.invoke(target);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String col(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    // tiny helper for a nicer message
    private static String pvStorageName(fr.elias.oreoEssentials.playervaults.PlayerVaultsService svc) {
        // best-effort hint; avoids adding getters on storage
        try {
            var f = svc.getClass().getDeclaredField("storage");
            f.setAccessible(true);
            Object st = f.get(svc);
            if (st == null) return "disabled";
            String n = st.getClass().getSimpleName().toLowerCase();
            if (n.contains("mongo")) return "mongodb";
            if (n.contains("yaml"))  return "yaml";
            return n;
        } catch (Throwable ignored) {
            return "ok";
        }
    }
}
//