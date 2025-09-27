package fr.elias.oreoEssentials.economy;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyBootstrap {
    private final Plugin plugin;
    private EconomyService service;

    public EconomyBootstrap(Plugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        // Try Vault first
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
                RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp =
                        Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
                if (rsp != null && rsp.getProvider() != null) {
                    service = new VaultEconomyService(rsp.getProvider());
                    plugin.getLogger().info("[Economy] Hooked Vault provider: " + rsp.getProvider().getName());
                    return;
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[Economy] Vault hook failed: " + t.getClass().getSimpleName());
        }

        // Fallback: internal YAML
        service = new YamlEconomyService(plugin);
        plugin.getLogger().info("[Economy] Using internal YAML balances.");
    }

    public void disable() {
        if (service instanceof YamlEconomyService yaml) yaml.save();
    }

    public EconomyService api() {
        if (service == null) throw new IllegalStateException("Economy not initialized. Call enable() first.");
        return service;
    }
}
