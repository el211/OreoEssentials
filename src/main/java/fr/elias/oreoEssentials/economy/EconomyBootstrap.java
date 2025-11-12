// File: src/main/java/fr/elias/oreoEssentials/economy/EconomyBootstrap.java
package fr.elias.oreoEssentials.economy;

import net.milkbowl.vault.economy.Economy;
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
        // 1) Try to hook an existing Vault provider (registered by another plugin or by OreoEssentials main)
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
                RegisteredServiceProvider<Economy> rsp =
                        Bukkit.getServicesManager().getRegistration(Economy.class);
                if (rsp != null && rsp.getProvider() != null) {
                    service = new VaultEconomyService(rsp.getProvider());
                    plugin.getLogger().info("[Economy] Hooked Vault provider: " + rsp.getProvider().getName());
                } else {
                    plugin.getLogger().info("[Economy] No Vault provider registered at boot.");
                }
            } else {
                plugin.getLogger().info("[Economy] Vault not present; using internal fallback.");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[Economy] Vault hook failed: " + t.getClass().getSimpleName());
        }

        // 2) Fallback: internal YAML economy layer (used only if no Vault provider available)
        if (service == null) {
            service = new YamlEconomyService(plugin);
            plugin.getLogger().info("[Economy] Using internal YAML balances.");
        }

        // Note: We do NOT register any Vault provider here.
        // The main plugin registers fr.elias.oreoEssentials.vault.VaultEconomyProvider at the desired priority.
    }

    public void disable() {
        if (service instanceof YamlEconomyService yaml) {
            yaml.save();
        }
        // No Vault unregister needed here; provider is owned/registered by the main plugin.
    }

    public EconomyService api() {
        if (service == null) {
            throw new IllegalStateException("Economy not initialized. Call enable() first.");
        }
        return service;
    }
}
