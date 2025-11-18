package fr.elias.oreoEssentials.commands;


import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

public interface OreoCommand {
    String name();
    List<String> aliases();
    String permission();
    String usage();
    boolean playerOnly(); // why: guard commands that make no sense from console

    boolean execute(CommandSender sender, String label, String[] args);
    // 🔽 Ajoute ça :
    default List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return Collections.emptyList(); // par défaut : rien
    }
}

