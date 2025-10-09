// File: src/main/java/fr/elias/oreoEssentials/commands/core/playercommands/CookCommand.java
package fr.elias.oreoEssentials.commands.core.playercommands;

import fr.elias.oreoEssentials.commands.OreoCommand;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;

import java.util.Iterator;
import java.util.List;

public class CookCommand implements OreoCommand {

    @Override public String name() { return "cook"; }
    @Override public List<String> aliases() { return List.of(); }
    @Override public String permission() { return "oreo.cook"; }
    @Override public String usage() { return "[amount|max]"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir() || hand.getAmount() <= 0) {
            p.sendMessage(ChatColor.RED + "Hold the item you want to smelt in your main hand.");
            return true;
        }

        // Find a matching cooking recipe (furnace / smoker / blast furnace / campfire)
        CookingRecipe<?> match = findCookingRecipeFor(hand);
        if (match == null) {
            p.sendMessage(ChatColor.RED + "No smelting recipe found for that item.");
            return true;
        }

        // How many to cook?
        int request;
        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("max") || args[0].equalsIgnoreCase("all")) {
                request = hand.getAmount();
            } else {
                try {
                    request = Math.max(1, Integer.parseInt(args[0]));
                } catch (NumberFormatException ex) {
                    p.sendMessage(ChatColor.RED + "Amount must be a number or 'max'.");
                    return true;
                }
            }
        } else {
            request = 1;
        }

        int canCook = Math.min(request, hand.getAmount());
        if (canCook <= 0) {
            p.sendMessage(ChatColor.RED + "Nothing to smelt.");
            return true;
        }

        ItemStack resultProto = match.getResult().clone();
        int perItemOut = Math.max(1, resultProto.getAmount());
        int totalOut = perItemOut * canCook;

        // Remove inputs
        hand.setAmount(hand.getAmount() - canCook);
        p.getInventory().setItemInMainHand(hand.getAmount() > 0 ? hand : null);

        // Give outputs (respect stack sizes)
        ItemStack give = resultProto.clone();
        give.setAmount(Math.min(give.getMaxStackSize(), totalOut));
        var leftover = p.getInventory().addItem(give);

        int remaining = totalOut - give.getAmount();
        // If more than one stack is needed
        while (remaining > 0) {
            ItemStack more = resultProto.clone();
            int add = Math.min(more.getMaxStackSize(), remaining);
            more.setAmount(add);
            leftover.putAll(p.getInventory().addItem(more));
            remaining -= add;
            if (!leftover.isEmpty()) break; // inventory full
        }

        // Drop leftovers if inv full
        if (!leftover.isEmpty()) {
            leftover.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
            p.sendMessage(ChatColor.YELLOW + "Your inventory was full; dropped some smelted items at your feet.");
        }

        p.playSound(p.getLocation(), Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.8f, 1.0f);
        p.sendMessage(ChatColor.GREEN + "Smelted "
                + ChatColor.AQUA + canCook + ChatColor.GREEN + " → "
                + ChatColor.AQUA + totalOut + ChatColor.GREEN + " "
                + ChatColor.GRAY + resultProto.getType().name().toLowerCase().replace('_', ' ') + ChatColor.GREEN + ".");

        return true;
    }

    private CookingRecipe<?> findCookingRecipeFor(ItemStack input) {
        Iterator<Recipe> it = org.bukkit.Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            if (r instanceof CookingRecipe<?> cr) {
                RecipeChoice choice = cr.getInputChoice();
                try {
                    if (choice != null && choice.test(input)) {
                        return cr;
                    }
                } catch (Throwable ignored) {
                    // some custom choices may throw on test; skip
                }
            }
        }
        return null;
    }
}
