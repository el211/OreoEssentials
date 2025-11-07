package fr.elias.oreoEssentials.modgui;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modgui.cfg.ModGuiConfig;
import fr.elias.oreoEssentials.modgui.menu.MainMenu;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.InventoryManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

public class ModGuiService implements TabExecutor {
    private final OreoEssentials plugin;
    private final InventoryManager inv;
    private final ModGuiConfig config;

    public ModGuiService(OreoEssentials plugin) {
        this.plugin = plugin;
        this.inv = plugin.getInvManager();

        this.config = new ModGuiConfig(plugin);
        this.config.load();

        // command is added in plugin.yml as "modgui"
        if (plugin.getCommand("modgui") != null) {
            plugin.getCommand("modgui").setExecutor(this);
            plugin.getCommand("modgui").setTabCompleter(this);
        }
    }

    public ModGuiConfig cfg() { return config; }

    public void openMain(Player p) {
        SmartInventory.builder()
                .manager(inv)
                .id("modgui-main")
                .provider(new MainMenu(plugin, this)) // <— use YOUR provider (not the private AliasEditor one)
                .size(6, 9)
                .title("Moderation Panel")
                .build()
                .open(p); // <— open for p, not "player"
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!p.hasPermission("oreo.modgui.open")) {
            p.sendMessage("§cNo permission: oreo.modgui.open");
            return true;
        }
        openMain(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }

    public void save() {
        try { config.save(); } catch (Exception ignored) {}
    }
}
