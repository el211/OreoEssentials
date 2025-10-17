// File: src/main/java/fr/elias/oreoEssentials/aliases/DynamicAliasRegistry.java
package fr.elias.oreoEssentials.aliases;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.*;

public final class DynamicAliasRegistry {
    private DynamicAliasRegistry() {}

    /** We keep strong refs so we can unregister on reload/disable. */
    private static final List<Command> REGISTERED = new ArrayList<>();

    /** Simple concrete command that delegates to our executor and exposes owning plugin. */
    private static final class DynamicAliasCommand extends Command implements PluginIdentifiableCommand {
        private final Plugin plugin;
        private final DynamicAliasExecutor exec;

        DynamicAliasCommand(Plugin plugin, String name, String desc, DynamicAliasExecutor exec) {
            super(name);
            this.plugin = plugin;
            this.exec = exec;
            setDescription(desc != null ? desc : "Oreo alias");
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            return exec.onAlias(sender, label, args);
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            // Keep it empty for now; you can wire custom tab later.
            return Collections.emptyList();
        }

        @Override
        public Plugin getPlugin() {
            return plugin;
        }
    }

    public static void register(Plugin plugin, String name, DynamicAliasExecutor exec, String desc) {
        CommandMap map = getCommandMap();
        if (map == null) return;

        // avoid collisions with existing commands
        Command existing = getKnownCommands(map).get(name.toLowerCase(Locale.ROOT));
        if (existing != null) {
            plugin.getLogger().warning("[Aliases] Command '" + name + "' already exists; skipping.");
            return;
        }

        DynamicAliasCommand cmd = new DynamicAliasCommand(plugin, name, desc, exec);
        map.register(plugin.getName(), cmd);
        REGISTERED.add(cmd);
    }

    public static void unregisterAll(Plugin plugin) {
        CommandMap map = getCommandMap();
        if (map == null) return;

        Map<String, Command> known = getKnownCommands(map);

        for (Command c : REGISTERED) {
            try {
                // Unregister from map
                c.unregister(map);

                // Clean up raw and namespaced keys
                if (known != null) {
                    String base = c.getName().toLowerCase(Locale.ROOT);
                    String ns   = plugin.getName().toLowerCase(Locale.ROOT) + ":" + base;
                    known.entrySet().removeIf(e -> {
                        String k = e.getKey();
                        Command v = e.getValue();
                        return v == c || k.equals(base) || k.equals(ns);
                    });
                }
            } catch (Throwable ignored) {}
        }
        REGISTERED.clear();
    }

    /** Always use reflection for broad API compatibility (Spigot/Paper). */
    private static CommandMap getCommandMap() {
        try {
            Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            return (CommandMap) f.get(Bukkit.getServer());
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[Aliases] Failed to access CommandMap: " + t.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Command> getKnownCommands(CommandMap map) {
        try {
            Field fKnown = SimpleCommandMap.class.getDeclaredField("knownCommands");
            fKnown.setAccessible(true);
            return (Map<String, Command>) fKnown.get(map);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[Aliases] Failed to access knownCommands: " + t.getMessage());
            return Collections.emptyMap();
        }
    }
}
