// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/HomesGuiCommand.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.rabbitmq.channel.PacketChannel;
import fr.elias.oreoEssentials.rabbitmq.packet.PacketManager;
import fr.elias.oreoEssentials.rabbitmq.packet.impl.HomeTeleportRequestPacket;
import fr.elias.oreoEssentials.services.HomeService;
import fr.minuskube.inv.SmartInventory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.UUID;

public class HomesGuiCommand implements OreoCommand {

    private final HomeService homes;

    public HomesGuiCommand(HomeService homes) {
        this.homes = homes;
    }

    @Override public String name() { return "homesgui"; }
    @Override public List<String> aliases() { return List.of("homesmenu", "homegui"); }
    @Override public String permission() { return "oreo.homes.gui"; }
    @Override public String usage() { return ""; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        // Open inventory
        SmartInventory inv = SmartInventory.builder()
                .id("oreo:homes")
                .provider(new HomesGuiProvider(homes))
                .size(6, 9)
                .title(ChatColor.DARK_GREEN + "Your Homes")
                .manager(OreoEssentials.get().getInvManager())
                .build();


        inv.open(p);
        return true;
    }

    /* ---------------- helpers exposed for provider ---------------- */

    static boolean crossServerTeleport(HomeService homes, Player p, String homeName) {
        final String key = homeName.toLowerCase();
        String targetServer = homes.homeServer(p.getUniqueId(), key);
        String localServer  = homes.localServer();
        if (targetServer == null) targetServer = localServer;

        if (targetServer.equalsIgnoreCase(localServer)) {
            var loc = homes.getHome(p.getUniqueId(), key);
            if (loc == null) {
                p.sendMessage(ChatColor.RED + "Home not found: " + ChatColor.YELLOW + homeName);
                return false;
            }
            p.teleport(loc);
            p.sendMessage(ChatColor.GREEN + "Teleported to " + ChatColor.AQUA + homeName + ChatColor.GREEN + ".");
            return true;
        }

        var cs = OreoEssentials.get().getCrossServerSettings();
        if (!cs.homes()) {
            p.sendMessage(ChatColor.RED + "Cross-server homes are disabled by server config.");
            p.sendMessage(ChatColor.GRAY + "Use " + ChatColor.AQUA + "/server " + targetServer + ChatColor.GRAY
                    + " then /home " + key);
            return false;
        }

        final var plugin = OreoEssentials.get();
        final PacketManager pm = plugin.getPacketManager();
        if (pm != null && pm.isInitialized()) {
            final String requestId = UUID.randomUUID().toString();
            HomeTeleportRequestPacket pkt = new HomeTeleportRequestPacket(p.getUniqueId(), key, targetServer, requestId);
            pm.sendPacket(PacketChannel.individual(targetServer), pkt);
        } else {
            p.sendMessage(ChatColor.RED + "Cross-server messaging is disabled. Ask an admin to enable RabbitMQ.");
            p.sendMessage(ChatColor.GRAY + "You can still /server " + targetServer + ChatColor.GRAY + " then /home " + key);
            return false;
        }

        return sendPlayerToServer(p, targetServer);
    }

    static boolean sendPlayerToServer(Player p, String serverName) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            p.sendPluginMessage(OreoEssentials.get(), "BungeeCord", b.toByteArray());
            p.sendMessage(ChatColor.YELLOW + "Sending you to " + ChatColor.AQUA + serverName
                    + ChatColor.YELLOW + "… (teleport on arrival).");
            return true;
        } catch (Exception ex) {
            Bukkit.getLogger().warning("[OreoEssentials] Connect plugin message failed: " + ex.getMessage());
            p.sendMessage(ChatColor.RED + "Failed to switch you to " + serverName + ".");
            return false;
        }
    }
}
