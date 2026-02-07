package fr.elias.oreoEssentials.commands.web;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.web.WebProfileService;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ProfileCreateCommand implements CommandExecutor, TabCompleter {

    private final OreoEssentials plugin;
    private final WebProfileService webService;

    public ProfileCreateCommand(OreoEssentials plugin) {
        this.plugin = plugin;
        this.webService = plugin.getWebProfileService();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.hasPermission("oreo.web.create")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (webService == null) {
            player.sendMessage(ChatColor.RED + "Web profiles are disabled on this server.");
            return true;
        }

        UUID uuid = player.getUniqueId();

        hasProfileAsync(uuid).thenAccept(exists -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            if (exists) {
                sendAlreadyExists(player);
                return;
            }

            createProfileAsync(player)
                    .thenAccept(credentials -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        if (credentials == null) {
                            player.sendMessage(ChatColor.RED + "Failed to create web profile. Please contact an administrator.");
                            return;
                        }
                        sendCreated(player, credentials);
                    }))
                    .exceptionally(ex -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) player.sendMessage(ChatColor.RED + "An error occurred while creating your profile.");
                        });
                        plugin.getLogger().severe("[WebProfile] Failed to create profile for " + player.getName() + ": " + ex.getMessage());
                        ex.printStackTrace();
                        return null;
                    });

        })).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) player.sendMessage(ChatColor.RED + "An error occurred while checking your profile.");
            });
            plugin.getLogger().severe("[WebProfile] hasProfile check failed for " + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace();
            return null;
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        return Collections.emptyList();
    }


    private void sendAlreadyExists(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "╔════════════════════════════════════════╗");
        player.sendMessage(ChatColor.GOLD + "║  " + ChatColor.RED + "❌ Profile Already Exists" + ChatColor.GOLD + "        ║");
        player.sendMessage(ChatColor.GOLD + "╚════════════════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "You already have a web profile!");
        player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.AQUA + "/oeweb reset" + ChatColor.GRAY + " to reset your password.");
        player.sendMessage("");
    }

    private void sendCreated(Player player, Object credentials) {
        String username = readString(credentials, "getUsername");
        String password = readString(credentials, "getPassword");

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "╔════════════════════════════════════════╗");
        player.sendMessage(ChatColor.GOLD + "║  " + ChatColor.GREEN + "✓ Web Profile Created!" + ChatColor.GOLD + "          ║");
        player.sendMessage(ChatColor.GOLD + "╚════════════════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "Your OreoEssentials Web Access:");
        player.sendMessage("");

        ClickEvent.Action copyAction = getCopyActionFallback();

        TextComponent usernameLabel = new TextComponent(ChatColor.GRAY + "  Username: " + ChatColor.WHITE);
        TextComponent usernameValue = new TextComponent(ChatColor.YELLOW + username);
        usernameValue.setClickEvent(new ClickEvent(copyAction, username));
        usernameValue.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.GREEN + "Click to copy!").create()));
        player.spigot().sendMessage(usernameLabel, usernameValue);

        TextComponent passwordLabel = new TextComponent(ChatColor.GRAY + "  Password: " + ChatColor.WHITE);
        TextComponent passwordValue = new TextComponent(ChatColor.YELLOW + password);
        passwordValue.setClickEvent(new ClickEvent(copyAction, password));
        passwordValue.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.GREEN + "Click to copy!").create()));
        player.spigot().sendMessage(passwordLabel, passwordValue);

        player.sendMessage("");

        TextComponent urlLabel = new TextComponent(ChatColor.GRAY + "  Web Panel: ");
        TextComponent urlValue = new TextComponent(ChatColor.AQUA + "" + ChatColor.UNDERLINE + "oreostudio.store/profile");
        urlValue.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://oreostudio.store/profile"));
        urlValue.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.GREEN + "Click to open in browser!").create()));
        player.spigot().sendMessage(urlLabel, urlValue);

        player.sendMessage("");
        player.sendMessage(ChatColor.RED + "⚠ " + ChatColor.YELLOW + "Save these credentials securely!");
        player.sendMessage(ChatColor.GRAY + "   You won't be able to see them again.");
        player.sendMessage("");
    }

    private ClickEvent.Action getCopyActionFallback() {
        try {
            return ClickEvent.Action.valueOf("COPY_TO_CLIPBOARD");
        } catch (Throwable ignored) {
            return ClickEvent.Action.SUGGEST_COMMAND;
        }
    }



    private CompletableFuture<Boolean> hasProfileAsync(UUID uuid) {
        try {
            var m = webService.getClass().getMethod("hasProfile", UUID.class);
            Object res = m.invoke(webService, uuid);

            if (res instanceof Boolean b) return CompletableFuture.completedFuture(b);
            if (res instanceof CompletableFuture<?> cf) return cf.thenApply(v -> (Boolean) v);

            if (res instanceof java.util.concurrent.CompletionStage<?> cs) {
                return cs.toCompletableFuture().thenApply(v -> (Boolean) v);
            }
        } catch (Throwable ignored) {}

        return CompletableFuture.completedFuture(false);
    }

    private CompletableFuture<Object> createProfileAsync(Player player) {
        try {
            var m = webService.getClass().getMethod("createProfile", Player.class);
            Object res = m.invoke(webService, player);

            if (res instanceof CompletableFuture<?> cf) return (CompletableFuture<Object>) cf;
            if (res instanceof java.util.concurrent.CompletionStage<?> cs) return (CompletableFuture<Object>) cs.toCompletableFuture();

            return CompletableFuture.completedFuture(res);
        } catch (Throwable t) {
            CompletableFuture<Object> failed = new CompletableFuture<>();
            failed.completeExceptionally(t);
            return failed;
        }
    }

    private String readString(Object obj, String getter) {
        if (obj == null) return "N/A";
        try {
            var m = obj.getClass().getMethod(getter);
            Object v = m.invoke(obj);
            return v == null ? "N/A" : String.valueOf(v);
        } catch (Throwable ignored) {
            return "N/A";
        }
    }
}
