package fr.elias.oreoEssentials.holograms;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public final class OreoHologramCommand implements TabExecutor {
    private final OreoHolograms api;

    public OreoHologramCommand(OreoHolograms api) { this.api = api; }

    private static boolean admin(CommandSender s) {
        if (s.hasPermission("OreoHolograms.admin") || s.hasPermission("oreo.holograms.admin")) return true;
        s.sendMessage("§cYou need OreoHolograms.admin to use this.");
        return false;
    }

    @Override public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!admin(s)) return true;
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) return help(s);

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "version" -> { s.sendMessage("§aOreoHolograms v1.0 (Paper/Folia Display Entities)"); return true; }
            case "list" -> { list(s); return true; }
            case "nearby" -> { if (!(s instanceof Player p)) { s.sendMessage("§cPlayer only."); return true; } nearby(p, a); return true; }
            case "create" -> { if (!(s instanceof Player p)) { s.sendMessage("§cPlayer only."); return true; } create(p, a); return true; }
            case "remove" -> { remove(s, a); return true; }
            case "copy" -> { copy(s, a); return true; }
            case "info" -> { info(s, a); return true; }
            case "edit" -> { edit(s, a); return true; }
            default -> { s.sendMessage("§cUnknown. Use /hologram help"); return true; }
        }
    }

    private boolean help(CommandSender s) {
        s.sendMessage("§e/Hologram help §7- this help");
        s.sendMessage("§e/Hologram list §7- list all holograms");
        s.sendMessage("§e/Hologram nearby <range> §7- list nearby");
        s.sendMessage("§e/Hologram create <text|item|block> <name> §7- create at your location");
        s.sendMessage("§e/Hologram remove <name> §7- remove");
        s.sendMessage("§e/Hologram copy <name> <newName> §7- duplicate");
        s.sendMessage("§e/Hologram info <name> §7- details");
        s.sendMessage("§e/Hologram edit <name> <subcmd...> §7- modify");
        s.sendMessage("§7Subcommands: moveHere | moveTo x y z [yaw] [pitch] | rotate deg | rotatepitch deg | translate dx dy dz |");
        s.sendMessage("§7 scale f | billboard center|fixed|vertical|horizontal | shadowStrength n | shadowRadius f |");
        s.sendMessage("§7 brightness block sky | visibilityDistance d | visibility ALL|MANUAL|PERMISSION_NEEDED |");
        s.sendMessage("§7 linkWithNpc <npc> | unlinkWithNpc");
        s.sendMessage("§7Text: setLine i text..., addLine text..., removeLine i, insertBefore i text..., insertAfter i text...,");
        s.sendMessage("§7 background COLOR|#RRGGBB|TRANSPARENT, textShadow true|false, textAlignment center|left|right, updateTextInterval <ticks|5s|2m>");
        s.sendMessage("§7Item: item (from hand)");
        s.sendMessage("§7Block: block <material>");
        return true;
    }

    private void list(CommandSender s) {
        s.sendMessage("§eHolograms:");
        for (OreoHologram h : api.all()) {
            var l = h.currentLocation();
            s.sendMessage(" §7- §f" + h.getName() + "§8 [" + h.getType() + "] §7@ §f" +
                    (l == null ? "?" : (l.getWorld().getName()+" "+l.getBlockX()+","+l.getBlockY()+","+l.getBlockZ())));
        }
    }

    private void nearby(Player p, String[] a) {
        double r = a.length >= 2 ? Double.parseDouble(a[1]) : 16.0;
        var pl = p.getLocation();
        var list = api.all().stream().filter(h -> {
            var l = h.currentLocation();
            return l != null && l.getWorld().equals(pl.getWorld()) && l.distance(pl) <= r;
        }).collect(Collectors.toList());
        p.sendMessage("§eNearby ("+r+" blocks):");
        for (var h : list) p.sendMessage(" - §f"+h.getName()+"§7 ("+h.getType()+")");
    }

    private void create(Player p, String[] a) {
        if (a.length < 3) { p.sendMessage("§cUsage: /hologram create <text|item|block> <name>"); return; }
        var type = OreoHologramType.valueOf(a[1].toUpperCase(Locale.ROOT));
        var name = a[2];
        var loc = OreoHologramLocation.of(p.getLocation());
        api.create(type, name, loc);
        p.sendMessage("§aCreated hologram §f"+name+"§a ("+type+").");
    }

    private void remove(CommandSender s, String[] a) {
        if (a.length < 2) { s.sendMessage("§cUsage: /hologram remove <name>"); return; }
        api.remove(a[1]); s.sendMessage("§aRemoved §f"+a[1]);
    }

    private void copy(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage("§cUsage: /hologram copy <name> <newName>"); return; }
        api.copy(a[1], a[2]); s.sendMessage("§aCopied §f"+a[1]+"§a -> §f"+a[2]);
    }

    private void info(CommandSender s, String[] a) {
        if (a.length < 2) { s.sendMessage("§cUsage: /hologram info <name>"); return; }
        var h = api.get(a[1]); if (h == null) { s.sendMessage("§cNot found."); return; }
        var d = h.toData();
        var l = h.currentLocation();
        s.sendMessage("§e"+d.name+" §7["+d.type+"] @ "+(l==null?"?":l.getWorld().getName()+" "+l.getBlockX()+","+l.getBlockY()+","+l.getBlockZ()));
        s.sendMessage("§7scale="+d.scale+" billboard="+d.billboard+" visDist="+d.visibilityDistance+" vis="+d.visibility);
        if (d.type == OreoHologramType.TEXT) s.sendMessage("§7lines="+d.lines.size()+" bg="+d.backgroundColor+" align="+d.textAlign+" shadow="+d.textShadow);
    }

    private void edit(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage("§cUsage: /hologram edit <name> <subcmd...>"); return; }
        var h = api.get(a[1]); if (h == null) { s.sendMessage("§cNot found."); return; }
        var sub = a[2].toLowerCase(Locale.ROOT);

        // location transforms
        if ((sub.equals("movehere") || sub.equals("position")) && s instanceof Player p) {
            h.moveTo(p.getLocation()); api.save(); s.sendMessage("§aMoved.");
            return;
        }
        if (sub.equals("moveto")) {
            if (a.length < 6) { s.sendMessage("§cUsage: moveto x y z [yaw] [pitch]"); return; }

            Location base = h.currentLocation();
            if (base == null && s instanceof Player p) base = p.getLocation();
            if (base == null) { s.sendMessage("§cWorld not loaded."); return; }

            double x = Double.parseDouble(a[3]);
            double y = Double.parseDouble(a[4]);
            double z = Double.parseDouble(a[5]);
            float yaw   = (a.length >= 7) ? Float.parseFloat(a[6]) : base.getYaw();
            float pitch = (a.length >= 8) ? Float.parseFloat(a[7]) : base.getPitch();

            // Create a fresh Location (preferred) to avoid mutating shared references
            Location target = new Location(base.getWorld(), x, y, z, yaw, pitch);

            h.moveTo(target);
            api.save();
            s.sendMessage("§aTeleported hologram.");
            return;
        }

        if (sub.equals("translate")) {
            if (a.length < 6) { s.sendMessage("§cUsage: translate dx dy dz"); return; }
            h.translate(Double.parseDouble(a[3]), Double.parseDouble(a[4]), Double.parseDouble(a[5])); api.save(); s.sendMessage("§aTranslated.");
            return;
        }
        if (sub.equals("rotate")) {
            if (a.length < 4) { s.sendMessage("§cUsage: rotate degrees"); return; }
            h.rotateYaw(Float.parseFloat(a[3])); api.save(); s.sendMessage("§aRotated yaw.");
            return;
        }
        if (sub.equals("rotatepitch")) {
            if (a.length < 4) { s.sendMessage("§cUsage: rotatepitch degrees"); return; }
            h.rotatePitch(Float.parseFloat(a[3])); api.save(); s.sendMessage("§aRotated pitch.");
            return;
        }

        // common props
        if (sub.equals("scale")) { h.setScale(Double.parseDouble(a[3])); api.save(); s.sendMessage("§aScale set."); return; }
        if (sub.equals("billboard")) { h.setBillboard(OreoHologramBillboard.from(a[3])); api.save(); s.sendMessage("§aBillboard set."); return; }
        if (sub.equals("shadowstrength")) { h.setShadow(Integer.parseInt(a[3]), (float)h.toData().shadowRadius); api.save(); s.sendMessage("§aShadow strength set."); return; }
        if (sub.equals("shadowradius")) { h.setShadow(h.toData().shadowStrength, Float.parseFloat(a[3])); api.save(); s.sendMessage("§aShadow radius set."); return; }
        if (sub.equals("brightness")) {
            if (a.length < 5) { s.sendMessage("§cUsage: brightness <block 0-15> <sky 0-15>"); return; }
            h.setBrightness(Integer.parseInt(a[3]), Integer.parseInt(a[4])); api.save(); s.sendMessage("§aBrightness set."); return;
        }
        if (sub.equals("visibilitydistance")) { h.setVisibilityDistance(Double.parseDouble(a[3])); api.save(); s.sendMessage("§aView range set."); return; }
        if (sub.equals("visibility")) {
            var v = OreoHologramVisibility.valueOf(a[3].toUpperCase(Locale.ROOT));
            h.setVisibilityMode(v); api.save(); s.sendMessage("§aVisibility mode: "+v); return;
        }
        if (sub.equals("linkwithnpc")) { s.sendMessage("§7NPC link stub recorded (FancyNpcs hook point)."); return; }
        if (sub.equals("unlinkwithnpc")) { s.sendMessage("§7NPC unlink stub done."); return; }

        // permission/manually (MANUAL or PERMISSION_NEEDED)
        if (sub.equals("viewpermission")) { h.setViewPermission(a.length>=4?a[3]:""); api.save(); s.sendMessage("§aView permission set."); return; }
        if (sub.equals("manualviewers")) {
            var names = Arrays.asList(Arrays.copyOfRange(a,3,a.length));
            h.setManualViewers(names); api.save(); s.sendMessage("§aManual viewers updated ("+names.size()+")."); return;
        }

        // TEXT
        if (h.getType() == OreoHologramType.TEXT) {
            TextOreoHologram t = (TextOreoHologram) h;
            switch (sub) {
                case "setline" -> {
                    if (a.length < 5) { s.sendMessage("§cUsage: setLine <line> <text...>"); return; }
                    int line = Integer.parseInt(a[3]);
                    String text = String.join(" ", Arrays.copyOfRange(a, 4, a.length));
                    t.setLine(line, colorize(text)); api.save(); s.sendMessage("§aLine set.");
                }
                case "addline" -> {
                    if (a.length < 4) { s.sendMessage("§cUsage: addLine <text...>"); return; }
                    String text = String.join(" ", Arrays.copyOfRange(a, 3, a.length));
                    t.addLine(colorize(text)); api.save(); s.sendMessage("§aLine added.");
                }
                case "removeline" -> {
                    if (a.length < 4) { s.sendMessage("§cUsage: removeLine <line>"); return; }
                    t.removeLine(Integer.parseInt(a[3])); api.save(); s.sendMessage("§aLine removed.");
                }
                case "insertbefore" -> {
                    if (a.length < 5) { s.sendMessage("§cUsage: insertBefore <line> <text...>"); return; }
                    int line = Integer.parseInt(a[3]);
                    String text = String.join(" ", Arrays.copyOfRange(a, 4, a.length));
                    t.insertBefore(line, colorize(text)); api.save(); s.sendMessage("§aInserted.");
                }
                case "insertafter" -> {
                    if (a.length < 5) { s.sendMessage("§cUsage: insertAfter <line> <text...>"); return; }
                    int line = Integer.parseInt(a[3]);
                    String text = String.join(" ", Arrays.copyOfRange(a, 4, a.length));
                    t.insertAfter(line, colorize(text)); api.save(); s.sendMessage("§aInserted.");
                }
                case "background" -> { t.setBackground(a[3]); api.save(); s.sendMessage("§aBackground set."); }
                case "textshadow" -> { t.setTextShadow(Boolean.parseBoolean(a[3])); api.save(); s.sendMessage("§aText shadow set."); }
                case "textalignment" -> { t.setAlignment(a[3]); api.save(); s.sendMessage("§aText alignment set."); }
                case "updatetextinterval" -> {
                    if (a.length < 4) { s.sendMessage("§cUsage: updateTextInterval <ticks|Xs|Xm>"); return; }
                    t.setUpdateIntervalTicks(parseInterval(a[3])); api.save(); s.sendMessage("§aUpdate interval set.");
                }
                default -> s.sendMessage("§cUnknown text edit subcmd.");
            }
            return;
        }

        // ITEM
        if (h.getType() == OreoHologramType.ITEM) {
            ItemOreoHologram ih = (ItemOreoHologram) h;
            if (sub.equals("item") && s instanceof Player p) {
                ItemStack inHand = p.getInventory().getItemInMainHand();
                if (inHand == null || inHand.getType() == Material.AIR) { s.sendMessage("§cHold an item in hand."); return; }
                ih.setItem(inHand); api.save(); s.sendMessage("§aItem set from hand.");
                return;
            }
            s.sendMessage("§cUnknown item edit subcmd.");
            return;
        }

        // BLOCK
        if (h.getType() == OreoHologramType.BLOCK) {
            BlockOreoHologram bh = (BlockOreoHologram) h;
            if (sub.equals("block")) {
                if (a.length < 4) { s.sendMessage("§cUsage: block <material>"); return; }
                bh.setBlockType(a[3]); api.save(); s.sendMessage("§aBlock set.");
                return;
            }
            s.sendMessage("§cUnknown block edit subcmd.");
            return;
        }

        s.sendMessage("§cNothing matched.");
    }

    private static long parseInterval(String s) {
        if (s.endsWith("s")) return Long.parseLong(s.substring(0, s.length()-1)) * 20L;
        if (s.endsWith("m")) return Long.parseLong(s.substring(0, s.length()-1)) * 1200L;
        return Long.parseLong(s); // ticks
    }

    private static String colorize(String s) { return s.replace("&","§"); }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        // perms gate
        if (!s.hasPermission("OreoHolograms.admin") && !s.hasPermission("oreo.holograms.admin")) {
            return List.of();
        }

        // /ohologram <arg1>
        if (a.length == 1) {
            return starts(List.of(
                    "help","version","list","nearby","create","remove","copy","info","edit"
            ), a[0]);
        }

        // /ohologram nearby <range>
        if (a.length == 2 && a[0].equalsIgnoreCase("nearby")) {
            return starts(List.of("8","16","32","64","128"), a[1]);
        }

        // /ohologram create <type>
        if (a.length == 2 && a[0].equalsIgnoreCase("create")) {
            return starts(List.of("text","item","block"), a[1]);
        }

        // /ohologram (remove|copy|info|edit) <name>
        if (a.length == 2 && Set.of("remove","copy","info","edit").contains(a[0].toLowerCase(Locale.ROOT))) {
            return starts(api.all().stream().map(OreoHologram::getName).collect(Collectors.toList()), a[1]);
        }

        // /ohologram edit <name> <subcmd>
        if (a.length == 3 && a[0].equalsIgnoreCase("edit")) {
            var h = api.get(a[1]);
            if (h == null) return List.of();

            List<String> subs = new ArrayList<>(List.of(
                    // common
                    "moveHere","moveTo","translate","rotate","rotatePitch",
                    "scale","billboard","shadowStrength","shadowRadius",
                    "brightness","visibilityDistance","visibility",
                    "viewPermission","manualViewers",
                    "linkWithNpc","unlinkWithNpc"
            ));

            if (h.getType() == OreoHologramType.TEXT) {
                subs.addAll(List.of(
                        "setLine","addLine","removeLine","insertBefore","insertAfter",
                        "background","textShadow","textAlignment","updateTextInterval"
                ));
            } else if (h.getType() == OreoHologramType.ITEM) {
                subs.add("item");
            } else if (h.getType() == OreoHologramType.BLOCK) {
                subs.add("block");
            }
            return starts(subs, a[2]);
        }

        // /ohologram edit <name> <subcmd> <value...>
        if (a.length == 4 && a[0].equalsIgnoreCase("edit")) {
            switch (a[2].toLowerCase(Locale.ROOT)) {
                case "billboard":
                    return starts(List.of("center","fixed","vertical","horizontal"), a[3]);
                case "visibility":
                    return starts(List.of("ALL","MANUAL","PERMISSION_NEEDED"), a[3]);
                case "textalignment":
                    return starts(List.of("left","center","right"), a[3]);
                case "textshadow":
                    return starts(List.of("true","false"), a[3]);
                case "background":
                    return starts(List.of("TRANSPARENT","#FFFFFF","#000000","RED","GREEN","BLUE","AQUA","YELLOW"), a[3]);
                case "moveto":
                    // suggest placeholders or some numbers; leave empty by default
                    return List.of();
                case "translate":
                case "rotate":
                case "rotatepitch":
                case "scale":
                case "shadowstrength":
                case "shadowradius":
                case "brightness":
                case "visibilitydistance":
                case "setline":
                case "removeline":
                case "insertbefore":
                case "insertafter":
                case "updatetextinterval":
                    // numeric arguments—don’t suggest
                    return List.of();
            }
        }

        return List.of();
    }


    private static List<String> starts(Collection<String> base, String pref) {
        String p = pref == null ? "" : pref.toLowerCase(Locale.ROOT);
        return base.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }
}
