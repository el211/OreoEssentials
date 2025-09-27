package fr.elias.oreoEssentials;

import fr.elias.oreoEssentials.services.HomeService;
import fr.elias.oreoEssentials.services.ConfigService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlaceholderAPIHook extends PlaceholderExpansion {
    private final OreoEssentials plugin;
    private final Economy economy; // may be null if Vault/econ disabled

    public PlaceholderAPIHook(OreoEssentials plugin) {
        this.plugin = plugin;
        var reg = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        this.economy = (reg != null) ? reg.getProvider() : null;
    }

    // Use a simple, lowercase identifier => %oreo_...%
    @Override public @NotNull String getIdentifier() { return "oreo"; }
    @Override public @NotNull String getAuthor()     { return String.join(", ", plugin.getDescription().getAuthors()); }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean canRegister()           { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String id) {
        if (player == null) return "";

        // -------- Economy --------
        if (id.equalsIgnoreCase("balance")) {
            if (economy == null) return "0";
            return String.valueOf(economy.getBalance(player));
        }

        // -------- Homes --------
        if (id.equalsIgnoreCase("homes_used")
                || id.equalsIgnoreCase("homes_max")
                || id.equalsIgnoreCase("homes")) {

            HomeService homes = plugin.getHomeService();
            ConfigService cfg  = plugin.getConfigService();

            int used = 0;
            if (homes != null && player.getUniqueId() != null) {
                try {
                    used = homes.homes(player.getUniqueId()).size();
                } catch (Exception ignored) {}
            }

            int max;
            if (cfg != null && player.isOnline() && player.getPlayer() != null) {
                max = cfg.getMaxHomesFor(player.getPlayer()); // permission-based cap
            } else if (cfg != null) {
                max = cfg.defaultMaxHomes(); // fallback when offline
            } else {
                max = 0;
            }

            if (id.equalsIgnoreCase("homes_used")) return String.valueOf(used);
            if (id.equalsIgnoreCase("homes_max"))  return String.valueOf(max);
            // id == homes -> "x/total"
            return used + "/" + max;
        }

        return null; // unknown placeholder
    }
}
