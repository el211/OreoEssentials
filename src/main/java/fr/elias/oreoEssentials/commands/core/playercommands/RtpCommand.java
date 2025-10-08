package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.rtp.RtpConfig;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class RtpCommand implements OreoCommand {

    @Override public String name() { return "rtp"; }
    @Override public List<String> aliases() { return List.of("randomtp"); }
    @Override public String permission() { return "oreo.rtp"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        OreoEssentials plugin = OreoEssentials.get();
        RtpConfig cfg = plugin.getRtpConfig();

        // Check world allowlist
        Set<String> allowedWorlds = cfg.allowedWorlds();
        if (!allowedWorlds.isEmpty() && !allowedWorlds.contains(p.getWorld().getName())) {
            p.sendMessage("§cRandom teleport is not allowed in this world.");
            return true;
        }

        // Compute best radius based on tier permissions oreo.tier.<key>
        int radius = cfg.bestRadiusFor(tier -> p.hasPermission("oreo.tier." + tier));

        p.sendMessage("§7Trying random teleport up to §b" + radius + "§7 blocks…");
        Location dest = findSafeLocation(p.getWorld(), p.getLocation(), radius, cfg);
        if (dest == null) {
            p.sendMessage("§cCouldn't find a safe spot. Try again.");
            return true;
        }

        boolean ok = p.teleport(dest);
        if (ok) {
            p.sendMessage("§aTeleported to §b" + dest.getBlockX() + "§7, §b" + dest.getBlockY() + "§7, §b" + dest.getBlockZ());
        } else {
            p.sendMessage("§cTeleport failed.");
        }
        return true;
    }

    private Location findSafeLocation(World world, Location center, int radius, RtpConfig cfg) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int attempts = cfg.attempts();
        int minY = Math.max(5, cfg.minY());
        int maxY = Math.min(255, cfg.maxY());
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
