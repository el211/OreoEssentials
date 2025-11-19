package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.rtp.RtpConfig;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class RtpCommand implements OreoCommand {

    @Override public String name() { return "rtp"; }
    @Override public List<String> aliases() { return List.of("randomtp"); }
    @Override public String permission() { return "oreo.rtp"; }
    @Override public String usage() { return "[world]"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        OreoEssentials plugin = OreoEssentials.get();
        RtpConfig cfg = plugin.getRtpConfig();

        if (!cfg.isEnabled()) {
            p.sendMessage("§cRandom teleport is currently disabled.");
            return true;
        }

        // 1) Decide target world (optionally from argument)
        String targetWorldName;

        if (args.length >= 1) {
            String requested = args[0];
            targetWorldName = requested;

            Set<String> allowed = cfg.allowedWorlds();
            if (!allowed.isEmpty() && !allowed.contains(targetWorldName)) {
                p.sendMessage("§cRandom teleport is not allowed in world §e" + requested + "§c.");
                return true;
            }
        } else {
            // Let config pick best world based on current world + default-target-world
            targetWorldName = cfg.chooseTargetWorld(p);
            if (targetWorldName == null) {
                p.sendMessage("§cNo valid world found for random teleport.");
                return true;
            }
        }

        // 2) Cross-server decision
        String localServer  = plugin.getConfigService().serverName();
        String targetServer = cfg.serverForWorld(targetWorldName);

        boolean crossEnabled = cfg.isCrossServerEnabled();
        boolean sameServer   = (targetServer == null) || targetServer.equalsIgnoreCase(localServer);

        if (crossEnabled && !sameServer) {
            // ✅ Cross-server mode:
            // Here we only switch the player to the correct server.
            // Then you can either:
            //  - let them run /rtp again there, OR
            //  - add a small join-listener on that server to auto-rtp once.
            p.sendMessage("§7Switching you to §b" + targetServer
                    + "§7 for random teleport in §b" + targetWorldName + "§7…");
            // Simple, generic way: rely on Bungee/Velocity /server command
            p.performCommand("server " + targetServer);
            return true;
        }

        // 3) Local RTP in targetWorldName (same server)
        World world = Bukkit.getWorld(targetWorldName);
        if (world == null) {
            p.sendMessage("§cWorld §e" + targetWorldName + "§c is not loaded on this server.");
            return true;
        }

        // Check allowlist for THAT world too
        if (!cfg.allowedWorlds().isEmpty() && !cfg.allowedWorlds().contains(world.getName())) {
            p.sendMessage("§cRandom teleport is not allowed in this world.");
            return true;
        }

        // 4) Compute radius using per-world + tier permissions
        Collection<String> tiers = List.of("oreo.tier.vip", "oreo.tier.mvp");
        int radius = cfg.radiusFor(p, tiers);

        // Center: if same world, use player's position; else use world spawn
        Location center = (world.equals(p.getWorld()))
                ? p.getLocation()
                : world.getSpawnLocation();

        p.sendMessage("§7Trying random teleport in §b" + world.getName()
                + "§7 up to §b" + radius + "§7 blocks…");

        Location dest = findSafeLocation(world, center, radius, cfg);
        if (dest == null) {
            p.sendMessage("§cCouldn't find a safe spot. Try again.");
            return true;
        }

        boolean ok = p.teleport(dest);
        if (ok) {
            p.sendMessage("§aTeleported to §b" + dest.getBlockX()
                    + "§7, §b" + dest.getBlockY()
                    + "§7, §b" + dest.getBlockZ()
                    + " §7in §b" + world.getName());
        } else {
            p.sendMessage("§cTeleport failed.");
        }
        return true;
    }

    private Location findSafeLocation(World world, Location center, int radius, RtpConfig cfg) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int attempts = cfg.attempts();
        int minY = Math.max(5, cfg.minY());
        int maxY = Math.min(world.getMaxHeight() - 1, cfg.maxY());
        Set<String> unsafe = cfg.unsafeBlocks();

        for (int i = 0; i < attempts; i++) {
            double angle = rnd.nextDouble(0, Math.PI * 2);
            double dist = rnd.nextDouble(0, Math.max(1, radius));
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);

            Chunk chunk = world.getChunkAt(new Location(world, x, 0, z));
            if (!chunk.isLoaded()) chunk.load(true);

            // scan downward to find ground
            int y = Math.min(maxY, world.getMaxHeight() - 1);
            while (y > minY && world.getBlockAt(x, y, z).isEmpty()) y--;

            if (y <= minY) continue;

            Block feet = world.getBlockAt(x, y + 1, z);
            Block head = world.getBlockAt(x, y + 2, z);
            Block ground = world.getBlockAt(x, y, z);

            if (!feet.isEmpty() || !head.isEmpty()) continue;

            String groundType = ground.getType().name();
            if (unsafe.contains(groundType)) continue;

            // avoid liquids
            if (ground.isLiquid()) continue;

            // found a spot
            Location tp = new Location(world, x + 0.5, y + 1.0, z + 0.5);
            tp.setYaw(ThreadLocalRandom.current().nextFloat() * 360f);
            tp.setPitch(0);
            return tp;
        }
        return null;
    }
}
