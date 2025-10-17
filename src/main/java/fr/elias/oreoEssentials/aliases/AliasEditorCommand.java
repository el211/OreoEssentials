package fr.elias.oreoEssentials.aliases;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public final class AliasEditorCommand implements CommandExecutor {

    private final AliasService service;

    public AliasEditorCommand(AliasService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("oreo.alias.editor")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                var map = service.all();
                if (map.isEmpty()) { sender.sendMessage("§7No aliases defined."); return true; }
                sender.sendMessage("§8—— §7Aliases (" + map.size() + ") §8——");
                map.values().forEach(a -> sender.sendMessage("§f/" + a.name + " §7→ §8" + a.runAs + " §7" + (a.enabled ? "§aENABLED" : "§cDISABLED")));
            }
            case "create" -> { // /aliaseditor create <name> <run-as:PLAYER|CONSOLE> <command...>
                if (args.length < 4) { sender.sendMessage("§cUsage: /aliaseditor create <name> <PLAYER|CONSOLE> <command...>"); return true; }
                String name = args[1].toLowerCase(Locale.ROOT);
                if (service.exists(name)) { sender.sendMessage("§cAlias already exists."); return true; }
                AliasService.AliasDef def = new AliasService.AliasDef();
                def.name = name;
                try { def.runAs = AliasService.RunAs.valueOf(args[2].toUpperCase(Locale.ROOT)); } catch (Throwable t) {
                    sender.sendMessage("§cRun-as must be PLAYER or CONSOLE."); return true;
                }
                def.commands = new ArrayList<>();
                def.commands.add(String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                service.put(def);
                service.save();
                service.applyRuntimeRegistration();
                sender.sendMessage("§aAlias /" + name + " created.");
            }
            case "set" -> { // /aliaseditor set <name> <command...>
                if (args.length < 3) { sender.sendMessage("§cUsage: /aliaseditor set <name> <command...>"); return true; }
                String name = args[1].toLowerCase(Locale.ROOT);
                var def = service.get(name);
                if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                def.commands = new ArrayList<>();
                def.commands.add(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                service.save();
                service.applyRuntimeRegistration();
                sender.sendMessage("§aAlias /" + name + " updated.");
            }
            case "addline" -> { // /aliaseditor addline <name> <command...>   (adds extra command; supports ; internally)
                if (args.length < 3) { sender.sendMessage("§cUsage: /aliaseditor addline <name> <command...>"); return true; }
                String name = args[1].toLowerCase(Locale.ROOT);
                var def = service.get(name);
                if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                def.commands.add(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                service.save();
                sender.sendMessage("§aAdded line to /" + name + ".");
            }
            case "enable" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /aliaseditor enable <name>"); return true; }
                var def = service.get(args[1]);
                if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                def.enabled = true; service.save(); service.applyRuntimeRegistration();
                sender.sendMessage("§aEnabled /" + def.name + ".");
            }
            case "disable" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /aliaseditor disable <name>"); return true; }
                var def = service.get(args[1]);
                if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                def.enabled = false; service.save(); service.applyRuntimeRegistration();
                sender.sendMessage("§eDisabled /" + def.name + ".");
            }
            case "runas" -> { // /aliaseditor runas <name> <PLAYER|CONSOLE>
                if (args.length < 3) { sender.sendMessage("§cUsage: /aliaseditor runas <name> <PLAYER|CONSOLE>"); return true; }
                var def = service.get(args[1]);
                if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                try { def.runAs = AliasService.RunAs.valueOf(args[2].toUpperCase(Locale.ROOT)); } catch (Throwable t) {
                    sender.sendMessage("§cRun-as must be PLAYER or CONSOLE."); return true;
                }
                service.save(); service.applyRuntimeRegistration();
                sender.sendMessage("§a/" + def.name + " now runs as " + def.runAs + ".");
            }
            case "cooldown" -> { // /aliaseditor cooldown <name> <seconds>
                if (args.length < 3) { sender.sendMessage("§cUsage: /aliaseditor cooldown <name> <seconds>"); return true; }
                var def = service.get(args[1]);
                if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                try { def.cooldownSeconds = Math.max(0, Integer.parseInt(args[2])); } catch (Exception e) {
                    sender.sendMessage("§cSeconds must be a number."); return true;
                }
                service.save();
                sender.sendMessage("§aCooldown set for /" + def.name + ": " + def.cooldownSeconds + "s.");
            }
            case "delete" -> { // /aliaseditor delete <name>
                if (args.length < 2) { sender.sendMessage("§cUsage: /aliaseditor delete <name>"); return true; }
                String name = args[1].toLowerCase(Locale.ROOT);
                if (!service.exists(name)) { sender.sendMessage("§cAlias not found."); return true; }
                service.remove(name);
                service.save();
                service.applyRuntimeRegistration();
                sender.sendMessage("§aAlias /" + name + " deleted.");
            }
            case "info" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /aliaseditor info <name>"); return true; }
                var def = service.get(args[1]);
                if (def == null) { sender.sendMessage("§cAlias not found."); return true; }
                sender.sendMessage("§8—— §7/" + def.name + " §8——");
                sender.sendMessage("§7Enabled: " + (def.enabled ? "§atrue" : "§cfalse"));
                sender.sendMessage("§7Run-as: §f" + def.runAs);
                sender.sendMessage("§7Cooldown: §f" + def.cooldownSeconds + "s");
                for (String cLine : def.commands) sender.sendMessage("§7• §f" + cLine);
            }
            case "reload" -> {
                service.load();
                service.applyRuntimeRegistration();
                sender.sendMessage("§aAliases reloaded.");
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage("§8—— §dAlias Editor §8——");
        s.sendMessage("§f/aliaseditor list");
        s.sendMessage("§f/aliaseditor create <name> <PLAYER|CONSOLE> <command...>");
        s.sendMessage("§f/aliaseditor set <name> <command...>");
        s.sendMessage("§f/aliaseditor addline <name> <command...>");
        s.sendMessage("§f/aliaseditor runas <name> <PLAYER|CONSOLE>");
        s.sendMessage("§f/aliaseditor cooldown <name> <seconds>");
        s.sendMessage("§f/aliaseditor enable|disable <name>");
        s.sendMessage("§f/aliaseditor info <name>");
        s.sendMessage("§f/aliaseditor delete <name>");
        s.sendMessage("§f/aliaseditor reload");
        s.sendMessage("§7Tips: Use %player%, %uuid%, %world%, %arg1%, %allargs%. Use ';' to chain commands.");
    }
}
