package fr.elias.oreoEssentials.daily;

import fr.elias.oreoEssentials.OreoEssentials;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.*;
import java.util.UUID;

public final class DailyService {

    private final OreoEssentials plugin;
    private final DailyConfig cfg;
    private final DailyMongoStore store;
    private final RewardsConfig rewards;

    public DailyService(OreoEssentials plugin, DailyConfig cfg, DailyMongoStore store, RewardsConfig rewards) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.store = store;
        this.rewards = rewards;
    }

    public String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private LocalDate today() { return LocalDate.now(ZoneId.systemDefault()); }

    public boolean canClaimToday(Player p) {
        var rec = store.ensure(p.getUniqueId(), p.getName());
        var last = rec.lastClaimDate();
        if (last == null) return true;
        if (cfg.availableOnNewDay) return !today().isEqual(last);
        return !today().isEqual(last); // simplified cadence
    }

    public int getStreak(UUID u) {
        var r = store.get(u);
        return (r == null) ? 0 : r.streak;
    }

    /** The day index in the cycle we should award on this claim (1..maxDay). */
    public int nextDayIndex(int currentStreak) {
        int max = Math.max(1, rewards.maxDay());
        if (cfg.resetWhenStreakCompleted) {
            // cycle 1..max, then wrap
            int next = currentStreak + 1;
            int wrapped = ((next - 1) % max) + 1;
            return wrapped;
        } else {
            // cap at max; keep awarding "Day max"
            return Math.min(currentStreak + 1, max);
        }
    }

    public boolean claim(Player p) {
        var r = store.ensure(p.getUniqueId(), p.getName());
        var t = today();
        var last = r.lastClaimDate();

        if (last != null && t.isEqual(last)) return false;

        int newStreak;
        if (last == null) newStreak = 1;
        else {
            long gap = Duration.between(last.atStartOfDay(), t.atStartOfDay()).toDays();
            if (gap == 1) newStreak = r.streak + 1;
            else if (cfg.pauseStreakWhenMissed) newStreak = r.streak;
            else if (cfg.skipMissedDays) newStreak = r.streak + 1;
            else newStreak = 1;
        }

        // Determine which "day" in rewards to execute
        int dayToAward = nextDayIndex(r.streak); // based on BEFORE updating streak
        RewardsConfig.DayDef def = rewards.day(dayToAward);

        // Update persistence first
        store.updateOnClaim(p.getUniqueId(), p.getName(), newStreak, t);

        // Execute reward commands (if any)
        if (def != null && def.commands != null) {
            for (String raw : def.commands) {
                String cmd = (raw == null ? "" : raw).trim();
                if (cmd.isEmpty() || cmd.equalsIgnoreCase("\"\"")) continue;
                cmd = cmd.replace("<playerName>", p.getName());
                // run as console by default
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
            if (def.message != null && !def.message.isEmpty()) {
                p.sendMessage(color(def.message));
            }
        }

        p.sendMessage(color(cfg.prefix + " &aClaimed &fDay " + dayToAward + " &areward! Streak: &f" + newStreak));
        return true;
    }
}
