package fr.elias.oreoEssentials.aliases;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AliasService {

    /* ------------------------------ Model ------------------------------ */

    public enum RunAs { PLAYER, CONSOLE }
    public enum LogicType { AND, OR }

    public static final class Check {
        /** raw user expression, e.g. "%ping%>=100", "permission:my.perm", "money>=1000", "%world%<-lobby-" */
        public String expr;
    }

    public static final class AliasDef {
        public String name;
        public boolean enabled = true;
        public RunAs runAs = RunAs.PLAYER;
        public int cooldownSeconds = 0;
        public List<String> commands = new ArrayList<>();

        // Checks / logic / fail message
        public List<Check> checks = new ArrayList<>();
        public LogicType logic = LogicType.AND; // how to combine checks (AND / OR)
        public String failMessage = "§cYou don't meet the requirements for %alias%.";
    }

    /* ------------------------------ State ------------------------------ */

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration cfg;

    // alias name -> definition
    private final Map<String, AliasDef> aliases = new ConcurrentHashMap<>();
    // cooldown key: alias|playerUUID -> last-used millis
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    /* --------------------------- Lifecycle ----------------------------- */

    public AliasService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "aliases.yml");
    }

    /** Loads aliases from aliases.yml (creates the file from resources if missing). */
    public void load() {
        // Ensure file exists, then read YAML
        if (!file.exists()) {
            try { plugin.saveResource("aliases.yml", false); } catch (Throwable ignored) {}
        }

        YamlConfiguration loaded;
        try {
            loaded = YamlConfiguration.loadConfiguration(file);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Aliases] Failed to load aliases.yml: " + t.getMessage());
            return;
        }

        this.cfg = loaded;
        aliases.clear();

        ConfigurationSection root = cfg.getConfigurationSection("aliases");
        if (root == null) {
            // No aliases section yet -> nothing to load
            plugin.getLogger().info("[Aliases] aliases.yml has no 'aliases' section (yet).");
            return;
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection a = root.getConfigurationSection(key);
            if (a == null) continue;

            AliasDef def = new AliasDef();
            def.name = key == null ? null : key.toLowerCase(Locale.ROOT);
            if (def.name == null || def.name.isBlank()) {
                plugin.getLogger().warning("[Aliases] Skipping alias with empty name node.");
                continue;
            }

            // Core fields
            def.enabled = a.getBoolean("enabled", true);
            def.runAs = parseRunAs(a.getString("run-as", "PLAYER"));
            def.cooldownSeconds = a.getInt("cooldown-seconds", 0);

            // Commands (safe default to empty list)
            List<String> cmds = a.getStringList("commands");
            def.commands = (cmds != null) ? new ArrayList<>(cmds) : new ArrayList<>();

            // ---- checks / logic / fail-message ----
            // Logic (AND/OR) with safe fallback
            String logicStr = String.valueOf(a.getString("logic", "AND")).toUpperCase(Locale.ROOT);
            try {
                def.logic = LogicType.valueOf(logicStr);
            } catch (Throwable ignored) {
                def.logic = LogicType.AND;
                plugin.getLogger().warning("[Aliases] Alias '" + def.name + "' has invalid logic '" + logicStr + "', defaulting to AND.");
            }

            // Fail message
            def.failMessage = a.getString("fail-message", def.failMessage);

            // Checks list
            List<String> rawChecks = a.getStringList("checks");
            def.checks = new ArrayList<>();
            if (rawChecks != null) {
                for (String rc : rawChecks) {
                    if (rc == null) continue;
                    String expr = rc.trim();
                    if (expr.isEmpty()) continue;
                    Check ch = new Check();
                    ch.expr = expr;
                    def.checks.add(ch);
                }
            }

            // Register into memory
            aliases.put(def.name, def);
        }

        plugin.getLogger().info("[Aliases] Loaded " + aliases.size() + " alias definition(s) from aliases.yml.");
    }

    /** Saves the current in-memory aliases to aliases.yml (including checks/logic/fail-message). */
    public void save() {
        YamlConfiguration out = new YamlConfiguration();
        ConfigurationSection root = out.createSection("aliases");

        for (AliasDef def : aliases.values()) {
            if (def.name == null || def.name.isBlank()) continue;
            ConfigurationSection a = root.createSection(def.name);
            a.set("enabled", def.enabled);
            a.set("run-as", def.runAs == null ? RunAs.PLAYER.name() : def.runAs.name());
            a.set("cooldown-seconds", def.cooldownSeconds);
            a.set("commands", def.commands == null ? Collections.emptyList() : def.commands);

            // checks as simple list of strings
            List<String> exprs = new ArrayList<>();
            if (def.checks != null) {
                for (Check ch : def.checks) {
                    if (ch != null && ch.expr != null && !ch.expr.trim().isEmpty()) {
                        exprs.add(ch.expr.trim());
                    }
                }
            }
            a.set("checks", exprs);

            // logic + fail-message
            a.set("logic", def.logic == null ? LogicType.AND.name() : def.logic.name());
            if (def.failMessage != null && !def.failMessage.isEmpty()) {
                a.set("fail-message", def.failMessage);
            } else {
                a.set("fail-message", "§cYou don't meet the requirements for %alias%.");
            }
        }

        try {
            out.save(file);
            this.cfg = out;
            plugin.getLogger().info("[Aliases] Saved " + aliases.size() + " alias definition(s) to aliases.yml.");
        } catch (Exception e) {
            plugin.getLogger().warning("[Aliases] Failed to save aliases.yml: " + e.getMessage());
        }
    }

    /** Registers runtime Bukkit commands for all enabled aliases. Safe to call after load()/save(). */
    public void applyRuntimeRegistration() {
        // Unregister previous dynamic commands to avoid duplicates
        DynamicAliasRegistry.unregisterAll(plugin);

        int count = 0;
        for (AliasDef def : aliases.values()) {
            if (!def.enabled) continue;
            // bind executor per-alias so it can read & execute live config
            DynamicAliasRegistry.register(
                    plugin,
                    def.name,
                    new DynamicAliasExecutor(plugin, this, def.name),
                    "Oreo alias"
            );
            count++;
        }
        plugin.getLogger().info("[Aliases] Registered " + count + " alias command(s).");
    }

    /** Cleanly unregisters all runtime aliases. Call from onDisable(). */
    public void shutdown() {
        try {
            DynamicAliasRegistry.unregisterAll(plugin);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Aliases] Unregister failed: " + t.getMessage());
        }
    }

    /* ----------------------------- API -------------------------------- */

    public Map<String, AliasDef> all() {
        return Collections.unmodifiableMap(aliases);
    }

    public boolean exists(String name) {
        return name != null && aliases.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public AliasDef get(String name) {
        if (name == null) return null;
        return aliases.get(name.toLowerCase(Locale.ROOT));
    }

    public void put(AliasDef def) {
        if (def == null || def.name == null) return;
        aliases.put(def.name.toLowerCase(Locale.ROOT), def);
    }

    public void remove(String name) {
        if (name == null) return;
        aliases.remove(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Cooldown gate. Returns true if the alias is allowed to run now and records usage;
     * false if the caller must wait longer.
     */
    public boolean checkAndTouchCooldown(String alias, UUID player, int seconds) {
        if (seconds <= 0 || player == null) return true;
        final String key = alias.toLowerCase(Locale.ROOT) + "|" + player;
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(key);
        if (last != null && (now - last) < seconds * 1000L) return false;
        cooldowns.put(key, now);
        return true;
    }

    /* --------------------------- Checks -------------------------------- */

    /**
     * Evaluate all checks on the alias according to its logic (AND / OR).
     * If there are no checks, returns true.
     */
    public boolean evaluateAllChecks(org.bukkit.command.CommandSender sender, AliasDef def) {
        if (def == null) return true;
        if (def.checks == null || def.checks.isEmpty()) return true;

        if (def.logic == LogicType.AND) {
            for (Check ch : def.checks) {
                if (!evaluateSingle(sender, ch == null ? null : ch.expr)) return false;
            }
            return true; // all passed
        } else { // OR
            for (Check ch : def.checks) {
                if (evaluateSingle(sender, ch == null ? null : ch.expr)) return true;
            }
            return false; // none passed
        }
    }

    private boolean evaluateSingle(org.bukkit.command.CommandSender sender, String exprRaw) {
        if (exprRaw == null || exprRaw.isEmpty()) return true;
        String expr = exprRaw.trim();

        // Permission
        if (expr.startsWith("permission:") || expr.startsWith("!permission:")) {
            boolean neg = expr.startsWith("!");
            String node = expr.substring(neg ? "!permission:".length() : "permission:".length()).trim();
            boolean has = sender.hasPermission(node);
            return neg ? !has : has;
        }

        // Money / Exp / Level shorthands
        // Format examples: "money>=1000", "exp<500", "level>=20"
        String lower = expr.toLowerCase(Locale.ROOT);
        if (lower.startsWith("money") || lower.startsWith("exp") || lower.startsWith("level")) {
            return evaluateNumericStat(sender, lower);
        }

        // Generic comparator between left (usually placeholder) and right literal
        // Supported ops (tested in order of length): >=, <=, !=, <-, !<-, |-, !|-, -|, !-|, >, <, =
        String[] ops = {">=", "<=", "!=", "<-", "!<-", "|-", "!|-", "-|", "!-|", ">", "<", "="};
        String op = null; int idx = -1;
        for (String candidate : ops) {
            idx = indexOfOp(expr, candidate);
            if (idx > -1) { op = candidate; break; }
        }
        if (op == null) {
            // No comparator found -> treat as pass (keeps old behavior liberal)
            return true;
        }

        String left = expr.substring(0, idx).trim();
        String right = expr.substring(idx + op.length()).trim();

        String leftResolved = resolve(sender, left);
        String rightResolved = stripQuotes(resolve(sender, right)); // allow "lobby-1"

        // numeric compares if both parse numbers
        if (isNumeric(leftResolved) && isNumeric(rightResolved)) {
            double l = Double.parseDouble(leftResolved);
            double r = Double.parseDouble(rightResolved);
            return switch (op) {
                case ">=" -> l >= r;
                case ">"  -> l >  r;
                case "<=" -> l <= r;
                case "<"  -> l <  r;
                case "="  -> l == r;
                case "!=" -> l != r;
                default   -> false;
            };
        }

        // string ops (case sensitive)
        return switch (op) {
            case "="   -> Objects.equals(leftResolved, rightResolved);
            case "!="  -> !Objects.equals(leftResolved, rightResolved);
            case "<-"  -> leftResolved.contains(rightResolved);
            case "!<-" -> !leftResolved.contains(rightResolved);
            case "|-"  -> leftResolved.startsWith(rightResolved);
            case "!|-" -> !leftResolved.startsWith(rightResolved);
            case "-|"  -> leftResolved.endsWith(rightResolved);
            case "!-|" -> !leftResolved.endsWith(rightResolved);
            default    -> false;
        };
    }

    private int indexOfOp(String s, String op) {
        // find op that is not inside quotes
        boolean inQ = false;
        char q = 0;
        for (int i = 0; i <= s.length() - op.length(); i++) {
            char c = s.charAt(i);
            if ((c == '"' || c == '\'') && (i == 0 || s.charAt(i - 1) != '\\')) {
                if (!inQ) { inQ = true; q = c; }
                else if (q == c) { inQ = false; q = 0; }
            }
            if (!inQ && s.regionMatches(i, op, 0, op.length())) return i;
        }
        return -1;
    }

    private boolean evaluateNumericStat(org.bukkit.command.CommandSender sender, String expr) {
        // supports >=, >, <=, <, =, !=
        String[] nops = {">=", "<=", "!=", ">", "<", "="};
        String op = null; int idx = -1;
        for (String candidate : nops) {
            idx = expr.indexOf(candidate);
            if (idx > -1) { op = candidate; break; }
        }
        if (op == null) return true;
        String leftKey = expr.substring(0, idx).trim();     // money | exp | level
        String rightStr = expr.substring(idx + op.length()).trim();
        double right;
        try { right = Double.parseDouble(rightStr); } catch (Exception e) { return true; }

        double left = switch (leftKey) {
            case "money" -> getMoney(sender);
            case "exp"   -> getExpPoints(sender);
            case "level" -> getExpLevel(sender);
            default      -> 0d;
        };

        return switch (op) {
            case ">=" -> left >= right;
            case ">"  -> left >  right;
            case "<=" -> left <= right;
            case "<"  -> left <  right;
            case "="  -> left == right;
            case "!=" -> left != right;
            default   -> true;
        };
    }

    private double getMoney(org.bukkit.command.CommandSender s) {
        if (!(s instanceof org.bukkit.entity.Player p)) return Double.MAX_VALUE; // console passes
        org.bukkit.plugin.Plugin vault = org.bukkit.Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null) return -1; // treat as not enough
        try {
            net.milkbowl.vault.economy.Economy econ =
                    org.bukkit.Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class).getProvider();
            return econ.getBalance(p);
        } catch (Throwable t) { return -1; }
    }

    private int getExpLevel(org.bukkit.command.CommandSender s) {
        if (s instanceof org.bukkit.entity.Player p) return p.getLevel();
        return Integer.MAX_VALUE;
    }

    private int getExpPoints(org.bukkit.command.CommandSender s) {
        if (s instanceof org.bukkit.entity.Player p) return Math.max(0, p.getTotalExperience());
        return Integer.MAX_VALUE;
    }

    private String resolve(org.bukkit.command.CommandSender sender, String token) {
        // strip surrounding quotes first for PAPI
        String s = stripQuotes(token);
        if (sender instanceof org.bukkit.entity.Player p) {
            org.bukkit.plugin.Plugin papi = org.bukkit.Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
            if (papi != null && (s.contains("%"))) {
                try {
                    Class<?> papiCls = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                    java.lang.reflect.Method m = papiCls.getMethod("setPlaceholders", org.bukkit.entity.Player.class, String.class);
                    s = (String) m.invoke(null, p, s);
                } catch (Throwable ignored) {}
            }
        }
        return s;
    }

    private String stripQuotes(String s) {
        if (s == null || s.length() < 2) return s;
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))
            return s.substring(1, s.length()-1);
        return s;
    }

    private boolean isNumeric(String s) {
        if (s == null) return false;
        try { Double.parseDouble(s); return true; } catch (Exception e) { return false; }
    }

    private RunAs parseRunAs(String s) {
        try { return RunAs.valueOf(s.toUpperCase(Locale.ROOT)); }
        catch (Throwable ignored) { return RunAs.PLAYER; }
    }
}
