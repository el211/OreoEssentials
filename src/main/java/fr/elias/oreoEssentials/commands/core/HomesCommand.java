// File: src/main/java/fr/elias/oreoEssentials/commands/core/HomesCommand.java
package fr.elias.oreoEssentials.commands.core;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.commands.OreoCommand;
import fr.elias.oreoEssentials.services.ConfigService;
import fr.elias.oreoEssentials.services.HomeService;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * /homes           -> list homes + count
 * /homes <name>    -> teleport to that home
 */
public class HomesCommand implements OreoCommand {

    private final HomeService homes;
    private final ConfigService cfg;

    public HomesCommand(HomeService homes) {
        this.homes = homes;
        this.cfg = OreoEssentials.get().getConfigService();
    }

    @Override public String name() { return "homes"; }
    @Override public List<String> aliases() { return List.of("listhomes"); }
    @Override public String permission() { return "oreo.homes"; }
    @Override public String usage() { return "[name]"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Player p = (Player) sender;

        // With name -> teleport
        if (args.length >= 1) {
            String raw = args[0];
            String key = normalize(raw);
            Location loc = getHomeReflect(key, p);
            if (loc == null) {
                p.sendMessage(ChatColor.RED + "Home not found: " + ChatColor.YELLOW + raw);
                var list = listNamesReflect(p);
                if (!list.isEmpty()) {
                    p.sendMessage(ChatColor.GRAY + "Available: " + ChatColor.AQUA +
                            String.join(ChatColor.GRAY + ", " + ChatColor.AQUA, list));
                }
                return true;
            }
            // Important: pass a Location to avoid the teleport ambiguity
            p.teleport(loc);
            p.sendMessage(ChatColor.GREEN + "Teleported to home " + ChatColor.AQUA + raw);
            return true;
        }

        // No args -> list
        List<String> list = listNamesReflect(p);
        int used = list.size();
        int max = maxHomes(p);

        if (used == 0) {
            p.sendMessage(ChatColor.YELLOW + "You have no homes. Use " + ChatColor.AQUA + "/sethome <name>");
            return true;
        }

        p.sendMessage(ChatColor.GOLD + "Homes (" + used + "/" + max + "): "
                + ChatColor.AQUA + String.join(ChatColor.GRAY + ", " + ChatColor.AQUA, list));
        p.sendMessage(ChatColor.GRAY + "Tip: " + ChatColor.AQUA + "/homes <name>" + ChatColor.GRAY + " to teleport.");
        return true;
    }

    /* ---------------- helpers ---------------- */

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    /** Try multiple possible method names to fetch a single home Location. */
    private Location getHomeReflect(String key, Player p) {
        UUID id = p.getUniqueId();

        // Try common signatures in order
        // Location getHome(UUID, String)
        Location loc = invokeLoc(homes, "getHome", new Class[]{UUID.class, String.class}, new Object[]{id, key});
        if (loc != null) return loc;

        // Location home(UUID, String)
        loc = invokeLoc(homes, "home", new Class[]{UUID.class, String.class}, new Object[]{id, key});
        if (loc != null) return loc;

        // Map-based lookup: Map<String, Location> getHomes(UUID) / homes(UUID) / listHomes(UUID)
        Map<String, Location> map = getMapOfHomesReflect(p);
        if (map != null) {
            Location l = map.get(key);
            if (l == null) {
                // some services keep original-case keys; try case-insensitive match
                for (Map.Entry<String, Location> e : map.entrySet()) {
                    if (normalize(e.getKey()).equals(key)) return e.getValue();
                }
            }
            return l;
        }

        return null;
    }

    /** Fetch the list of home names from various possible APIs. */
    private List<String> listNamesReflect(Player p) {
        // Prefer a Map<String, Location>
        Map<String, Location> map = getMapOfHomesReflect(p);
        if (map != null) {
            return map.keySet().stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        // Or Set/List of names
        Collection<?> coll = getCollectionOfHomesReflect(p);
        if (coll != null) {
            return coll.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        // Fallback empty
        return Collections.emptyList();
    }

    private Map<String, Location> getMapOfHomesReflect(Player p) {
        UUID id = p.getUniqueId();
        // Try: Map<String, Location> getHomes(UUID)
        Object o = invoke(homes, "getHomes", new Class[]{UUID.class}, new Object[]{id});
        if (o instanceof Map<?,?> m) return castMapToLoc(m);

        // Try: Map<String, Location> homes(UUID)
        o = invoke(homes, "homes", new Class[]{UUID.class}, new Object[]{id});
        if (o instanceof Map<?,?> m2) return castMapToLoc(m2);

        // Try: Map<String, Location> listHomes(UUID)
        o = invoke(homes, "listHomes", new Class[]{UUID.class}, new Object[]{id});
        if (o instanceof Map<?,?> m3) return castMapToLoc(m3);

        return null;
    }

    private Collection<?> getCollectionOfHomesReflect(Player p) {
        UUID id = p.getUniqueId();
        // Try: Set<String>/List<String> getHomeNames(UUID)
        Object o = invoke(homes, "getHomeNames", new Class[]{UUID.class}, new Object[]{id});
        if (o instanceof Collection<?> c) return c;

        // Try: homes(UUID) returning a Collection
        o = invoke(homes, "homes", new Class[]{UUID.class}, new Object[]{id});
        if (o instanceof Collection<?> c2) return c2;

        // Try: listHomes(UUID)
        o = invoke(homes, "listHomes", new Class[]{UUID.class}, new Object[]{id});
        if (o instanceof Collection<?> c3) return c3;

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Location> castMapToLoc(Map<?,?> m) {
        Map<String, Location> out = new HashMap<>();
        for (Map.Entry<?,?> e : m.entrySet()) {
            Object k = e.getKey();
            Object v = e.getValue();
            if (k == null || v == null) continue;
            if (v instanceof Location loc) {
                out.put(k.toString(), loc);
            }
        }
        return out;
    }

    private int maxHomes(Player p) {
        // Use your ConfigService if present; otherwise show just the used count
        try {
            if (cfg != null) {
                // Prefer a player-specific cap if your ConfigService provides it
                try {
                    Method m = cfg.getClass().getMethod("getMaxHomesFor", Player.class);
                    Object r = m.invoke(cfg, p);
                    if (r instanceof Number n) return n.intValue();
                } catch (NoSuchMethodException ignored) {
                    // fallback to defaultMaxHomes()
                }
                try {
                    Method m2 = cfg.getClass().getMethod("defaultMaxHomes");
                    Object r2 = m2.invoke(cfg);
                    if (r2 instanceof Number n2) return n2.intValue();
                } catch (NoSuchMethodException ignored) {
                    // no method, fall through
                }
            }
        } catch (Throwable ignored) {}
        // Unknown cap -> just display used/?
        return -1;
    }

    /* --------------- reflection helpers --------------- */

    private static Object invoke(Object target, String method, Class<?>[] sig, Object[] args) {
        try {
            Method m = target.getClass().getMethod(method, sig);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Location invokeLoc(Object target, String method, Class<?>[] sig, Object[] args) {
        Object o = invoke(target, method, sig, args);
        return (o instanceof Location) ? (Location) o : null;
    }
}
