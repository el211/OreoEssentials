package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.WarpDirectory;
import fr.elias.oreoEssentials.services.WarpService;
import fr.elias.oreoEssentials.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SetWarpCommand implements OreoCommand {
    private final WarpService warps;

    public SetWarpCommand(WarpService warps) { this.warps = warps; }

    @Override public String name() { return "setwarp"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.setwarp"; }
    @Override public String usage() { return "<name>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            Lang.send(sender, "warps.usage.setwarp", Map.of("label", label), (sender instanceof Player p) ? p : null);
            return true;
        }
        Player p = (Player) sender;
        String name = args[0].trim().toLowerCase(Locale.ROOT);

        warps.setWarp(name, p.getLocation());
        Lang.send(p, "warps.set", Map.of("warp", name), p);

        // record owner server if directory exists (cross-server)
        WarpDirectory warpDir = OreoEssentials.get().getWarpDirectory();
        if (warpDir != null) {
            String local = OreoEssentials.get().getConfig().getString("server.name", Bukkit.getServer().getName());
            warpDir.setWarpServer(name, local);
            p.sendMessage("§7(Cross-server) Warp owner set to §b" + local + "§7.");
        }
        return true;
    }
}
