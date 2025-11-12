package fr.elias.oreoEssentials.chat;

import net.luckperms.api.LuckPermsProvider;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormatManager {
    private final CustomConfig customYmlManager;

    // Existing: <#RRGGBB>
    private static final Pattern HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");

    // New: <gradient:#rrggbb:#rrggbb[:#rrggbb...]>TEXT</gradient>
    private static final Pattern GRADIENT_PATTERN =
            Pattern.compile("<gradient:([^>]+)>(.*?)</gradient>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public FormatManager(CustomConfig customYmlManager) {
        this.customYmlManager = customYmlManager;
    }

    public String formatMessage(Player p, String message) {
        String group;
        try {
            group = LuckPermsProvider.get().getUserManager()
                    .getUser(p.getUniqueId())
                    .getPrimaryGroup();
        } catch (Throwable t) {
            group = "default";
        }

        // 1) Load format
        String format = customYmlManager.getCustomConfig().getString("chat." + group);
        if (format == null) {
            format = customYmlManager.getCustomConfig()
                    .getString("chat.default", "&7%player_displayname% » &f%chat_message%");
        }

        // 2) Inject raw message immediately
        if (message == null) message = "";
        format = format.replace("%chat_message%", message);

        // 3) Prepare names (optionally strip existing colors so gradient/hex applies cleanly)
        boolean stripNameColors = customYmlManager.getCustomConfig().getBoolean("chat.strip-name-colors", true);
        String displayName = p.getDisplayName();
        String plainName   = p.getName();
        if (stripNameColors) {
            displayName = stripAll(displayName);
            plainName   = stripAll(plainName);
        }

        // 4) Replace name placeholders BEFORE colorization/gradient
        if (format.contains("%player_displayname%")) {
            format = format.replace("%player_displayname%", displayName);
        }
        if (format.contains("%player_name%")) {
            // If both placeholders are used, keep %player_name% as plain
            String nameToUse = format.contains("%player_displayname%") ? plainName : displayName;
            format = format.replace("%player_name%", nameToUse);
        }

        // 5) Apply gradients first (so they can wrap everything, including injected names)
        format = applyGradients(format);

        // 6) Then resolve <#RRGGBB> and legacy & codes
        format = colorize(format);

        return format;
    }

    /* --------------------------- Gradient engine --------------------------- */

    private String applyGradients(String input) {
        Matcher m = GRADIENT_PATTERN.matcher(input);
        StringBuffer out = new StringBuffer();

        while (m.find()) {
            String colorsSpec = m.group(1); // "#ff00aa:#00ffaa:#0000ff" (':' ',' '; ';' separators ok)
            String text       = m.group(2);

            List<int[]> stops = parseColorStops(colorsSpec);
            if (stops.size() < 2 || text.isEmpty()) {
                // Not enough colors or empty text: just remove the tag and keep raw text
                m.appendReplacement(out, Matcher.quoteReplacement(text));
                continue;
            }

            String colored = applyGradientToText(text, stops);
            m.appendReplacement(out, Matcher.quoteReplacement(colored));
        }
        m.appendTail(out);
        return out.toString();
    }

    private List<int[]> parseColorStops(String spec) {
        String[] parts = spec.split("[:;,\\s]+");
        List<int[]> list = new ArrayList<>();
        for (String p : parts) {
            p = p.trim();
            if (p.isEmpty()) continue;
            if (p.startsWith("#")) p = p.substring(1);
            if (p.length() == 3) { // allow #abc shorthand
                p = "" + p.charAt(0) + p.charAt(0) + p.charAt(1) + p.charAt(1) + p.charAt(2) + p.charAt(2);
            }
            if (p.length() != 6) continue;
            try {
                int r = Integer.parseInt(p.substring(0, 2), 16);
                int g = Integer.parseInt(p.substring(2, 4), 16);
                int b = Integer.parseInt(p.substring(4, 6), 16);
                list.add(new int[]{r, g, b});
            } catch (Exception ignored) {}
        }
        return list;
    }

    private String applyGradientToText(String raw, List<int[]> stops) {
        // We want to color only visible characters; pass-through existing §/& color codes
        StringBuilder out = new StringBuilder(raw.length() * 10);

        // First, count visible code points
        List<Integer> visibleIndexes = new ArrayList<>();
        char[] arr = raw.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            char c = arr[i];
            if (isColorCodeStart(c) && i + 1 < arr.length && isLegacyCodeChar(arr[i + 1])) {
                // keep both as-is; skip from gradient span
                i++; // skip code char
                continue;
            }
            visibleIndexes.add(i);
        }

        int n = visibleIndexes.size();
        if (n == 0) return raw;

        // Gradient over multiple segments between stops
        int segments = stops.size() - 1;

        for (int v = 0, i = 0; i < arr.length; i++) {
            char c = arr[i];
            if (isColorCodeStart(c) && i + 1 < arr.length && isLegacyCodeChar(arr[i + 1])) {
                // passthrough legacy code (don't color next)
                out.append('§').append(Character.toLowerCase(arr[i + 1]));
                i++; // skip the code char
                continue;
            }

            if (v < n && i == visibleIndexes.get(v)) {
                // Compute color at this visible index
                double t = (n == 1) ? 0.0 : (double) v / (double) (n - 1);
                int[] rgb = sampleMultiStop(stops, t);
                out.append(toMcHex(rgb[0], rgb[1], rgb[2])).append(c);
                v++;
            } else {
                // Non-visible (part of something else?), just echo
                out.append(c);
            }
        }

        return out.toString();
    }

    private int[] sampleMultiStop(List<int[]> stops, double t) {
        if (t <= 0) return stops.get(0);
        if (t >= 1) return stops.get(stops.size() - 1);
        double scaled = t * (stops.size() - 1);
        int idx = (int) Math.floor(scaled);
        double localT = scaled - idx;

        int[] a = stops.get(idx);
        int[] b = stops.get(idx + 1);
        int r = (int) Math.round(a[0] + (b[0] - a[0]) * localT);
        int g = (int) Math.round(a[1] + (b[1] - a[1]) * localT);
        int bC = (int) Math.round(a[2] + (b[2] - a[2]) * localT);
        return new int[]{clamp8(r), clamp8(g), clamp8(bC)};
    }

    private int clamp8(int v) { return Math.min(255, Math.max(0, v)); }

    private boolean isColorCodeStart(char c) { return c == '§' || c == '&'; }

    private boolean isLegacyCodeChar(char c) {
        c = Character.toLowerCase(c);
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'k' && c <= 'o') || c == 'r' || c == 'x';
    }

    private String toMcHex(int r, int g, int b) {
        String hex = String.format(Locale.ROOT, "%02X%02X%02X", r, g, b);
        // §x§R§R§G§G§B§B
        return "§x§" + hex.charAt(0) + "§" + hex.charAt(1) + "§" + hex.charAt(2) + "§" + hex.charAt(3) + "§" + hex.charAt(4) + "§" + hex.charAt(5);
    }

    /* ----------------------------- Colorize ----------------------------- */

    private String colorize(String input) {
        // Convert <#RRGGBB> then &-codes → §
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder mc = new StringBuilder("§x");
            for (char c : hex.toCharArray()) mc.append('§').append(c);
            matcher.appendReplacement(buffer, mc.toString());
        }
        matcher.appendTail(buffer);
        return buffer.toString().replace('&', '§');
    }

    private String stripAll(String s) {
        if (s == null) return "";
        // First translate & to § (so stripColor catches both)
        String t = s.replace('&', '§');
        return ChatColor.stripColor(t);
    }
}
