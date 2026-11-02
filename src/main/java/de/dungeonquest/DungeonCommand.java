package de.dungeonquest;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DungeonCommand implements CommandExecutor, TabCompleter {

    private static final String[] DIRECTIONS = {
            "Osten", "Südosten", "Süden", "Südwesten", "Westen", "Nordwesten", "Norden", "Nordosten"
    };

    private final DungeonQuest plugin;

    public DungeonCommand(DungeonQuest plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender s, String text) {
        s.sendMessage(DungeonQuest.PREFIX + DungeonQuest.c(text));
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("dungeonquest.admin")) return true;
        msg(s, "&cDafür hast du keine Berechtigung.");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        DungeonManager mgr = plugin.manager();

        switch (sub) {
            case "locate", "suchen", "finden" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "&cNur für Spieler.");
                    return true;
                }
                if (!plugin.getConfig().getBoolean("locate-erlaubt", true) && !p.hasPermission("dungeonquest.admin")) {
                    msg(sender, "&cDie Dungeon-Suche ist deaktiviert.");
                    return true;
                }
                Dungeon best = null;
                double bd = Double.MAX_VALUE;
                for (Dungeon d : mgr.all()) {
                    if (!d.worldName.equals(p.getWorld().getName())) continue;
                    double dist = Math.hypot(d.ox - p.getLocation().getX(), d.oz - p.getLocation().getZ());
                    if (dist < bd) {
                        bd = dist;
                        best = d;
                    }
                }
                if (best == null) {
                    msg(sender, "&cIn dieser Welt gibt es keinen Dungeon.");
                    return true;
                }
                double dx = best.ox - p.getLocation().getX();
                double dz = best.oz - p.getLocation().getZ();
                double deg = Math.toDegrees(Math.atan2(dz, dx));
                int idx = ((int) Math.round(deg / 45.0) + 8) % 8;
                int rounded = (int) (Math.round(bd / 10.0) * 10);
                p.setCompassTarget(new Location(p.getWorld(), best.ox, best.surfaceY, best.oz));
                msg(sender, "&7Nächster Dungeon: " + best.difficulty.colored() + " &7- ca. &f" + rounded
                        + " &7Blöcke Richtung &f" + DIRECTIONS[idx] + "&7. Dein Kompass zeigt auf ihn.");
            }
            case "list", "liste" -> {
                if (!admin(sender)) return true;
                if (mgr.all().isEmpty()) {
                    msg(sender, "&7Keine Dungeons vorhanden.");
                    return true;
                }
                for (Dungeon d : mgr.all()) {
                    String state = d.respawnAt > 0 ? "&8besiegt" : d.bossSpawned ? "&cBoss aktiv" : "&aBereit";
                    String kindName = d.kind == Dungeon.Kind.TOWER ? "Turm" : "Tief";
                    msg(sender, "&f#" + d.id + " &8" + kindName + " " + d.difficulty.colored() + " &7(" + d.worldName + " " + d.ox + " / "
                            + d.oz + ") " + state);
                }
            }
            case "create", "erstellen" -> {
                if (!admin(sender)) return true;
                if (!(sender instanceof Player p)) {
                    msg(sender, "&cNur für Spieler.");
                    return true;
                }
                Difficulty df = args.length > 1 ? Difficulty.parse(args[1]) : null;
                if (df == null) {
                    msg(sender, "&cBenutzung: /dungeon create <leicht|normal|schwer|albtraum|inferno> [turm]");
                    return true;
                }
                boolean tower = args.length > 2 && args[2].equalsIgnoreCase("turm");
                msg(sender, "&7Baue " + (tower ? "Turm" : "Dungeon") + " ... (kann kurz ruckeln)");
                Location l = p.getLocation();
                Dungeon d = tower
                        ? plugin.builder().tryPlaceTower(p.getWorld(), df, l.getBlockX(), l.getBlockZ(), mgr.newId(), false)
                        : plugin.builder().tryPlace(p.getWorld(), df, l.getBlockX(), l.getBlockZ(), mgr.newId(), false);
                if (d == null) {
                    msg(sender, "&cHier ist kein Platz (falsche Welt oder zu tief).");
                    return true;
                }
                mgr.add(d);
                msg(sender, "&a#" + d.id + " (" + df.colored() + "&a) gebaut. "
                        + (tower ? "Der Turm steht an deiner Position." : "Der Eingang liegt bei dir, der Dungeon erstreckt sich nach Osten (+X)."));
            }
            case "generate", "generieren" -> {
                if (!admin(sender)) return true;
                int n = 1;
                if (args.length > 1) {
                    try {
                        n = Math.max(1, Math.min(30, Integer.parseInt(args[1])));
                    } catch (NumberFormatException ex) {
                        msg(sender, "&cBenutzung: /dungeon generate <anzahl> [stufe]");
                        return true;
                    }
                }
                Difficulty df = args.length > 2 ? Difficulty.parse(args[2]) : null;
                String kindArg = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "";
                World w = sender instanceof Player p ? p.getWorld() : mgr.targetWorld();
                if (w == null) return true;
                List<DungeonManager.Job> queue = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    Difficulty jd = df != null ? df : Difficulty.values()[(int) (Math.random() * 3)];
                    Dungeon.Kind jk = kindArg.equals("turm") ? Dungeon.Kind.TOWER
                            : kindArg.equals("tief") ? Dungeon.Kind.UNDERGROUND
                            : (Math.random() < 0.5 ? Dungeon.Kind.TOWER : Dungeon.Kind.UNDERGROUND);
                    queue.add(new DungeonManager.Job(jd, jk));
                }
                msg(sender, "&7Erzeuge " + n + " Dungeon(s)/Türme zufällig in der Welt. Das passiert nach und nach im Hintergrund.");
                mgr.generate(queue, w, sender);
            }
            case "remove", "entfernen" -> {
                if (!admin(sender)) return true;
                Integer id = parseId(args, 1);
                if (id == null) {
                    msg(sender, "&cBenutzung: /dungeon remove <id>");
                    return true;
                }
                if (mgr.remove(id)) {
                    msg(sender, "&aDungeon #" + id + " aus der Liste entfernt (die Blöcke bleiben in der Welt stehen).");
                } else {
                    msg(sender, "&cUnbekannte ID.");
                }
            }
            case "reset" -> {
                if (!admin(sender)) return true;
                Integer id = parseId(args, 1);
                Dungeon d = id == null ? null : mgr.get(id);
                if (d == null) {
                    msg(sender, "&cBenutzung: /dungeon reset <id>");
                    return true;
                }
                mgr.fullReset(d);
                msg(sender, "&aDungeon #" + id + " zurückgesetzt.");
            }
            case "tp" -> {
                if (!admin(sender)) return true;
                if (!(sender instanceof Player p)) return true;
                Integer id = parseId(args, 1);
                Dungeon d = id == null ? null : mgr.get(id);
                if (d == null || d.world() == null) {
                    msg(sender, "&cBenutzung: /dungeon tp <id>");
                    return true;
                }
                p.teleport(d.entrance());
                msg(sender, "&7Teleportiert zu Dungeon #" + d.id + ".");
            }
            case "item" -> {
                if (!admin(sender)) return true;
                if (!(sender instanceof Player p)) return true;
                String id = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
                boolean isRare = LootFactory.RARE_IDS.contains(id);
                if (!isRare && !LootFactory.LEGENDARY_IDS.contains(id)) {
                    msg(sender, "&cLegendär: " + String.join(", ", LootFactory.LEGENDARY_IDS));
                    msg(sender, "&cSelten: " + String.join(", ", LootFactory.RARE_IDS));
                    return true;
                }
                ItemStack it = isRare ? plugin.loot().rare(id) : plugin.loot().legendary(id);
                p.getInventory().addItem(it);
                msg(sender, "&aItem erhalten.");
            }
            case "reload" -> {
                if (!admin(sender)) return true;
                plugin.reloadConfig();
                Difficulty.loadAll(plugin.getConfig());
                msg(sender, "&aConfig neu geladen. (Gilt für neu erzeugte Räume und Gegner.)");
            }
            default -> {
                msg(sender, "&6DungeonQuest &7- Befehle:");
                sender.sendMessage(DungeonQuest.c("&e/dungeon locate &7- nächsten Dungeon suchen"));
                if (sender.hasPermission("dungeonquest.admin")) {
                    sender.sendMessage(DungeonQuest.c("&e/dungeon list &7- alle Dungeons anzeigen"));
                    sender.sendMessage(DungeonQuest.c("&e/dungeon create <stufe> [turm] &7- Dungeon/Turm an deiner Position bauen"));
                    sender.sendMessage(DungeonQuest.c("&e/dungeon generate <anzahl> [stufe] [turm|tief] &7- zufällig erzeugen"));
                    sender.sendMessage(DungeonQuest.c("&e/dungeon tp <id> &7- zum Dungeon teleportieren"));
                    sender.sendMessage(DungeonQuest.c("&e/dungeon reset <id> &7- Dungeon zurücksetzen"));
                    sender.sendMessage(DungeonQuest.c("&e/dungeon remove <id> &7- Dungeon aus der Liste löschen"));
                    sender.sendMessage(DungeonQuest.c("&e/dungeon item <name> &7- legendäres Item erhalten"));
                    sender.sendMessage(DungeonQuest.c("&e/dungeon reload &7- Config neu laden"));
                }
            }
        }
        return true;
    }

    private Integer parseId(String[] args, int index) {
        if (args.length <= index) return null;
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean isAdmin = sender.hasPermission("dungeonquest.admin");
        if (args.length == 1) {
            out.add("locate");
            if (isAdmin) {
                out.addAll(List.of("list", "create", "generate", "tp", "reset", "remove", "item", "reload"));
            }
        } else if (args.length == 2 && isAdmin) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create" -> out.addAll(List.of("leicht", "normal", "schwer", "albtraum", "inferno"));
                case "item" -> {
                    out.addAll(LootFactory.LEGENDARY_IDS);
                    out.addAll(LootFactory.RARE_IDS);
                }
                case "tp", "reset", "remove" -> {
                    for (Dungeon d : plugin.manager().all()) out.add(String.valueOf(d.id));
                }
                default -> { }
            }
        } else if (args.length == 3 && isAdmin && args[0].equalsIgnoreCase("generate")) {
            out.addAll(List.of("leicht", "normal", "schwer", "albtraum", "inferno"));
        } else if (args.length == 3 && isAdmin && args[0].equalsIgnoreCase("create")) {
            out.add("turm");
        } else if (args.length == 4 && isAdmin && args[0].equalsIgnoreCase("generate")) {
            out.addAll(List.of("turm", "tief"));
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
