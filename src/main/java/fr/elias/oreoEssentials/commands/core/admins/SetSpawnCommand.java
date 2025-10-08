package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.SpawnDirectory;
import fr.elias.oreoEssentials.services.SpawnService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class SetSpawnCommand implements OreoCommand {
    private final SpawnService spawn;

    public SetSpawnCommand(SpawnService spawn) { this.spawn = spawn; }

    @Override public String name() { return "setspawn"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.setspawn"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;

        spawn.setSpawn(p.getLocation());
        p.sendMessage(ChatColor.GREEN + "Spawn set.");

        // record owner server if directory exists (cross-server)
        SpawnDirectory spawnDir = OreoEssentials.get().getSpawnDirectory();
        if (spawnDir != null) {
            String local = OreoEssentials.get().getConfig().getString("server.name", Bukkit.getServer().getName());
            spawnDir.setSpawnServer(local);
            p.sendMessage(ChatColor.GRAY + "(Cross-server) Spawn owner set to " + ChatColor.AQUA + local + ChatColor.GRAY + ".");
        }
        return true;
    }
}
