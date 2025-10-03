package fr.elias.oreoEssentials.commands.core.admins;

import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.util.MojangSkinFetcher;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class HeadCommand implements OreoCommand, org.bukkit.command.TabCompleter {
    @Override public String name() { return "head"; }
    @Override public List<String> aliases() { return List.of("playerhead"); }
    @Override public String permission() { return "oreo.head"; }
    @Override public String usage() { return "<player>"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player self = (Player) sender;

        if (args.length < 1) {
            self.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <player>");
            return true;
        }

        String target = args[0];
        PlayerProfile prof = null;

        var online = Bukkit.getPlayerExact(target);
        if (online != null) prof = online.getPlayerProfile();
        if (prof == null) {
            UUID u = MojangSkinFetcher.fetchUuid(target);
            if (u != null) prof = MojangSkinFetcher.fetchProfileWithTextures(u, target);
        }
        if (prof == null) {
            self.sendMessage(ChatColor.RED + "Could not resolve " + target + "'s head.");
            return true;
        }

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwnerProfile(prof);
            meta.setDisplayName(ChatColor.AQUA + (prof.getName() != null ? prof.getName() : target) + ChatColor.GRAY + "'s Head");
            skull.setItemMeta(meta);
        }

        HashMap<Integer, ItemStack> leftover = self.getInventory().addItem(skull);
        if (!leftover.isEmpty()) {
            self.getWorld().dropItemNaturally(self.getLocation(), skull);
            self.sendMessage(ChatColor.YELLOW + "Inventory full—dropped the head at your feet.");
        } else {
            self.sendMessage(ChatColor.GREEN + "Gave you " + ChatColor.AQUA + target + ChatColor.GREEN + "'s head.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
