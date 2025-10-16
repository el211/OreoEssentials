package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public class UuidCommand implements OreoCommand {
    @Override public String name() { return "uuid"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.uuid"; }
    @Override public String usage() { return "[player]"; }
    @Override public boolean playerOnly() { return false; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        // Resolve target (self by default for players)
        OfflinePlayer target;
        if (args.length >= 1) {
            if (!sender.hasPermission("oreo.uuid.others")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to view others' UUIDs.");
                return true;
            }
            target = Bukkit.getOfflinePlayer(args[0]);
        } else {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <player>");
                return true;
            }
            target = p;
        }

        String display = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        UUID uuid = target.getUniqueId();

        sender.sendMessage(ChatColor.GOLD + "UUID of " + ChatColor.AQUA + display + ChatColor.GOLD + ": " + ChatColor.AQUA + uuid);

        // Floodgate (Bedrock) info — best-effort, works when the player is a Floodgate player (usually online).
        try {
            Class<?> apiCls = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiCls.getMethod("getInstance").invoke(null);

            boolean isBedrock = (boolean) apiCls.getMethod("isFloodgatePlayer", UUID.class).invoke(api, uuid);
            if (isBedrock) {
                Object fgPlayer = apiCls.getMethod("getPlayer", UUID.class).invoke(api, uuid);
                UUID bedrockUuid = null;

                try { bedrockUuid = (UUID) fgPlayer.getClass().getMethod("getCorrectUniqueId").invoke(fgPlayer); }
                catch (NoSuchMethodException ignored1) {
                    try { bedrockUuid = (UUID) fgPlayer.getClass().getMethod("getJavaUniqueId").invoke(fgPlayer); }
                    catch (NoSuchMethodException ignored2) {
                        try { bedrockUuid = (UUID) fgPlayer.getClass().getMethod("getUniqueId").invoke(fgPlayer); }
                        catch (NoSuchMethodException ignored3) { bedrockUuid = uuid; }
                    }
                }

                sender.sendMessage(ChatColor.GRAY + "Bedrock: " + ChatColor.AQUA + bedrockUuid);
            }
        } catch (ClassNotFoundException e) {
            // Floodgate not installed — silently ignore
        } catch (Throwable t) {
            OreoEssentials.get().getLogger().fine("[/uuid] Floodgate reflect failed: " + t.getMessage());
        }

        return true;
    }
}
