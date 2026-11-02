package de.dungeonquest;

import de.dungeonquest.BossType.Ability;
import de.dungeonquest.Dungeon.MobMeta;
import de.dungeonquest.Dungeon.Room;
import de.dungeonquest.Dungeon.RoomType;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarStyle;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class DungeonManager implements Listener {

    private final DungeonQuest plugin;
    private final Map<Integer, Dungeon> dungeons = new LinkedHashMap<>();
    private final Map<UUID, Dungeon> tracked = new HashMap<>();
    private final Random rnd = new Random();
    private int nextId = 1;
    private boolean initialized = false;
    private boolean towersInitialized = false;

    public record Job(Difficulty df, Dungeon.Kind kind) { }
    private BukkitTask task;

    public DungeonManager(DungeonQuest plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Verwaltung
    // ------------------------------------------------------------------

    public Collection<Dungeon> all() {
        return dungeons.values();
    }

    public Dungeon get(int id) {
        return dungeons.get(id);
    }

    public int newId() {
        return nextId++;
    }

    public void add(Dungeon d) {
        dungeons.put(d.id, d);
        if (d.id >= nextId) nextId = d.id + 1;
        save();
    }

    public boolean remove(int id) {
        Dungeon d = dungeons.remove(id);
        if (d == null) return false;
        softReset(d);
        save();
        return true;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 10L);
        // Uebriggebliebene Dungeon-Mobs aus frueheren Laeufen entfernen
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                cleanupStray(e);
            }
        }
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (Dungeon d : new ArrayList<>(dungeons.values())) {
            softReset(d);
        }
        save();
    }

    private void cleanupStray(Entity e) {
        if (e.getPersistentDataContainer().has(plugin.dungeonKey(), PersistentDataType.INTEGER)
                && !tracked.containsKey(e.getUniqueId())) {
            e.remove();
        }
    }

    // ------------------------------------------------------------------
    //  Speichern / Laden
    // ------------------------------------------------------------------

    public void load() {
        File f = new File(plugin.getDataFolder(), "data.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        initialized = y.getBoolean("initialized", false);
        towersInitialized = y.getBoolean("towers-initialized", false);
        nextId = y.getInt("next-id", 1);
        ConfigurationSection sec = y.getConfigurationSection("dungeons");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;
            Difficulty df = Difficulty.parse(s.getString("difficulty", "EASY"));
            if (df == null) df = Difficulty.EASY;
            String world = s.getString("world", "world");
            if (Bukkit.getWorld(world) == null) {
                plugin.getLogger().warning("Welt '" + world + "' für Dungeon " + key + " nicht gefunden - übersprungen.");
                continue;
            }
            int id;
            try {
                id = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                continue;
            }
            Dungeon.Kind kind = "TOWER".equalsIgnoreCase(s.getString("kind", "UNDERGROUND"))
                    ? Dungeon.Kind.TOWER : Dungeon.Kind.UNDERGROUND;
            Dungeon d = new Dungeon(id, world, s.getInt("x"), s.getInt("z"),
                    s.getInt("floor-y"), s.getInt("surface-y"), df, kind);
            d.respawnAt = s.getLong("respawn-at", 0L);
            dungeons.put(id, d);
            if (id >= nextId) nextId = id + 1;
        }
    }

    public void save() {
        File f = new File(plugin.getDataFolder(), "data.yml");
        YamlConfiguration y = new YamlConfiguration();
        y.set("initialized", initialized);
        y.set("towers-initialized", towersInitialized);
        y.set("next-id", nextId);
        for (Dungeon d : dungeons.values()) {
            String p = "dungeons." + d.id + ".";
            y.set(p + "world", d.worldName);
            y.set(p + "x", d.ox);
            y.set(p + "z", d.oz);
            y.set(p + "floor-y", d.floorY);
            y.set(p + "surface-y", d.surfaceY);
            y.set(p + "difficulty", d.difficulty.name());
            y.set(p + "kind", d.kind.name());
            y.set(p + "respawn-at", d.respawnAt);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(f);
        } catch (IOException ex) {
            plugin.getLogger().warning("data.yml konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    //  Automatische Erzeugung
    // ------------------------------------------------------------------

    public World targetWorld() {
        World w = Bukkit.getWorld(plugin.getConfig().getString("welt", "world"));
        if (w == null && !Bukkit.getWorlds().isEmpty()) w = Bukkit.getWorlds().get(0);
        return w;
    }

    public void autoGenerateIfNeeded() {
        if (!plugin.getConfig().getBoolean("auto-dungeons.aktiv", true)) return;
        if (initialized && towersInitialized) return;
        World w = targetWorld();
        if (w == null) return;
        List<Job> queue = new ArrayList<>();
        int[] defUnder = {4, 4, 3, 2, 1};
        int[] defTower = {3, 3, 2, 2, 1};
        if (!initialized) {
            for (Difficulty df : Difficulty.values()) {
                int n = plugin.getConfig().getInt("auto-dungeons.anzahl." + df.name(), defUnder[df.ordinal()]);
                for (int i = 0; i < n; i++) queue.add(new Job(df, Dungeon.Kind.UNDERGROUND));
            }
        }
        if (!towersInitialized) {
            for (Difficulty df : Difficulty.values()) {
                int n = plugin.getConfig().getInt("auto-dungeons.turm-anzahl." + df.name(), defTower[df.ordinal()]);
                for (int i = 0; i < n; i++) queue.add(new Job(df, Dungeon.Kind.TOWER));
            }
        }
        initialized = true;
        towersInitialized = true;
        save();
        generate(queue, w, null);
    }

    public void generate(List<Job> queue, World w, CommandSender who) {
        long interval = Math.max(10L, plugin.getConfig().getLong("auto-dungeons.versuch-intervall-ticks", 40L));
        plugin.getLogger().info("Erzeuge " + queue.size() + " Dungeons in Welt '" + w.getName() + "' ...");
        new BukkitRunnable() {
            int idx = 0;
            int tries = 0;

            @Override
            public void run() {
                if (idx >= queue.size()) {
                    cancel();
                    plugin.getLogger().info("Dungeon-Erzeugung abgeschlossen. Gesamt: " + dungeons.size());
                    if (who != null) who.sendMessage(DungeonQuest.PREFIX + DungeonQuest.c("&aFertig! Es gibt jetzt &f" + dungeons.size() + " &aDungeons."));
                    return;
                }
                Job job = queue.get(idx);
                Difficulty df = job.df();
                int[] pos = randomPos(w, df);
                tries++;
                boolean placed = false;
                if (!tooClose(w, pos[0], pos[1]) && insideBorder(w, pos[0], pos[1])) {
                    int newId = newId();
                    Dungeon d = job.kind() == Dungeon.Kind.TOWER
                            ? plugin.builder().tryPlaceTower(w, df, pos[0], pos[1], newId, true)
                            : plugin.builder().tryPlace(w, df, pos[0], pos[1], newId, true);
                    if (d != null) {
                        add(d);
                        placed = true;
                        plugin.getLogger().info((d.kind == Dungeon.Kind.TOWER ? "Turm #" : "Dungeon #") + d.id + " (" + df.display + ") bei " + d.ox + " / " + d.oz + " gebaut.");
                    } else {
                        nextId--; // ID wieder freigeben
                    }
                }
                if (placed) {
                    idx++;
                    tries = 0;
                } else if (tries >= 40) {
                    plugin.getLogger().warning("Kein Platz für einen " + df.display + "-Dungeon gefunden - übersprungen.");
                    idx++;
                    tries = 0;
                }
            }
        }.runTaskTimer(plugin, 20L, interval);
    }

    private int[] randomPos(World w, Difficulty df) {
        double min = plugin.getConfig().getDouble("auto-dungeons.ringe." + df.name() + ".min", 300);
        double max = plugin.getConfig().getDouble("auto-dungeons.ringe." + df.name() + ".max", 1200);
        Location sp = w.getSpawnLocation();
        double ang = rnd.nextDouble() * Math.PI * 2;
        double dist = min + rnd.nextDouble() * Math.max(1, max - min);
        return new int[]{sp.getBlockX() + (int) (Math.cos(ang) * dist), sp.getBlockZ() + (int) (Math.sin(ang) * dist)};
    }

    private boolean insideBorder(World w, int x, int z) {
        return w.getWorldBorder().isInside(new Location(w, x, 64, z))
                && w.getWorldBorder().isInside(new Location(w, x + 160, 64, z));
    }

    private boolean tooClose(World w, int x, int z) {
        double min = plugin.getConfig().getDouble("auto-dungeons.mindestabstand", 500);
        for (Dungeon d : dungeons.values()) {
            if (!d.worldName.equals(w.getName())) continue;
            if (Math.hypot(d.ox - x, d.oz - z) < min) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    //  Registrierung von Mobs
    // ------------------------------------------------------------------

    public void register(Dungeon d, Room room, LivingEntity le, MobMeta meta) {
        d.mobs.put(le.getUniqueId(), meta);
        if (room != null) room.mobs.add(le.getUniqueId());
        tracked.put(le.getUniqueId(), d);
        le.getPersistentDataContainer().set(plugin.dungeonKey(), PersistentDataType.INTEGER, d.id);
    }

    private void removeMob(Dungeon d, UUID id) {
        d.mobs.remove(id);
        tracked.remove(id);
        for (Room r : d.rooms) {
            if (r.mobs.remove(id)) {
                if (r.type == RoomType.COMBAT && r.active && !r.cleared && r.mobs.isEmpty()) {
                    r.cleared = true;
                    World w = d.world();
                    if (w != null) {
                        for (Player p : w.getPlayers()) {
                            if (d.inside(p.getLocation())) {
                                p.sendMessage(DungeonQuest.PREFIX + DungeonQuest.c("&aRaum gesäubert!"));
                                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
                            }
                        }
                    }
                }
            }
        }
    }

    private void removeAllMobs(Dungeon d) {
        for (UUID id : new ArrayList<>(d.mobs.keySet())) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
            tracked.remove(id);
        }
        d.mobs.clear();
    }

    // ------------------------------------------------------------------
    //  Zuruecksetzen
    // ------------------------------------------------------------------

    public void softReset(Dungeon d) {
        removeAllMobs(d);
        if (d.boss != null) {
            Entity be = Bukkit.getEntity(d.boss);
            if (be != null) be.remove();
            tracked.remove(d.boss);
        }
        d.boss = null;
        d.bossSpawned = false;
        d.phase = 1;
        if (d.bar != null) {
            d.bar.removeAll();
            d.bar = null;
        }
        for (Room r : d.rooms) {
            r.active = false;
            r.cleared = false;
            r.mobs.clear();
        }
        d.idleTicks = 0;
        d.bossIdle = 0;
    }

    public void fullReset(Dungeon d) {
        softReset(d);
        d.respawnAt = 0;
        d.needsRefill = true;
        save();
    }

    // ------------------------------------------------------------------
    //  Haupt-Schleife (alle 10 Ticks)
    // ------------------------------------------------------------------

    private void tick() {
        long now = System.currentTimeMillis();
        for (Dungeon d : new ArrayList<>(dungeons.values())) {
            World w = d.world();
            if (w == null) continue;

            List<Player> ps = new ArrayList<>();
            for (Player p : w.getPlayers()) {
                if (p.getGameMode() != GameMode.SPECTATOR && !p.isDead() && d.inside(p.getLocation())) {
                    ps.add(p);
                }
            }
            d.playersInside = Math.max(1, ps.size());

            if (d.respawnAt > 0 && now >= d.respawnAt) {
                d.respawnAt = 0;
                softReset(d);
                d.needsRefill = true;
                save();
            }

            if (ps.isEmpty()) {
                if (d.respawnAt == 0 && (!d.mobs.isEmpty() || d.bossSpawned)) {
                    d.idleTicks += 10;
                    if (d.idleTicks >= 600) softReset(d);
                }
                continue;
            }
            d.idleTicks = 0;
            if (d.respawnAt > 0) continue;

            if (d.needsRefill) {
                plugin.builder().refillChests(d);
                d.needsRefill = false;
            }

            cleanupMobs(d);

            for (Room r : d.rooms) {
                if (r.type == RoomType.COMBAT && !r.active) {
                    for (Player p : ps) {
                        if (r.contains(p.getLocation())) {
                            activateRoom(d, r, ps);
                            break;
                        }
                    }
                } else if (r.type == RoomType.BOSS && !d.bossSpawned) {
                    for (Player p : ps) {
                        if (r.contains(p.getLocation())) {
                            spawnBoss(d, r, ps);
                            break;
                        }
                    }
                }
            }

            if (d.bossSpawned && d.boss != null) bossTick(d, ps);
            eliteTick(d);
        }
    }

    private void cleanupMobs(Dungeon d) {
        for (UUID id : new ArrayList<>(d.mobs.keySet())) {
            Entity e = Bukkit.getEntity(id);
            if (e == null || !e.isValid() || e.isDead()) {
                removeMob(d, id);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Raeume
    // ------------------------------------------------------------------

    private void activateRoom(Dungeon d, Room r, List<Player> ps) {
        World w = d.world();
        if (w == null) return;
        r.active = true;
        int n = ps.size();
        double groupMobs = plugin.getConfig().getDouble("skalierung.pro-extra-spieler-mob-anzahl", 0.5);
        int count = (int) Math.round(d.difficulty.mobsPerRoom * (1 + groupMobs * (n - 1))) + rnd.nextInt(3);

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Location loc = randomSpot(w, r);
            if (loc == null) continue;
            Player target = ps.get(rnd.nextInt(ps.size()));
            if (plugin.mobs().spawn(d, r, loc, n, target) != null) spawned++;
        }
        if (spawned == 0) {
            r.cleared = true;
            return;
        }
        for (Player p : ps) {
            if (r.contains(p.getLocation())) {
                p.sendTitle(DungeonQuest.c("&c\u2694 Kampf!"), DungeonQuest.c("&7" + spawned + " Gegner"), 5, 30, 10);
                p.playSound(p.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 0.8f, 0.8f);
            }
        }
    }

    private Location randomSpot(World w, Room r) {
        for (int i = 0; i < 15; i++) {
            int x = r.minX + rnd.nextInt(r.maxX - r.minX + 1);
            int z = r.minZ + rnd.nextInt(r.maxZ - r.minZ + 1);
            if (w.getBlockAt(x, r.floorY + 1, z).isEmpty() && w.getBlockAt(x, r.floorY + 2, z).isEmpty()) {
                return new Location(w, x + 0.5, r.floorY + 1, z + 0.5);
            }
        }
        return null;
    }

    private Room roomAt(Dungeon d, Location l) {
        for (Room r : d.rooms) {
            if ((r.type == RoomType.COMBAT || r.type == RoomType.BOSS) && r.contains(l)) return r;
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Elite-Faehigkeiten
    // ------------------------------------------------------------------

    private void eliteTick(Dungeon d) {
        for (Map.Entry<UUID, MobMeta> en : new ArrayList<>(d.mobs.entrySet())) {
            MobMeta meta = en.getValue();
            if (meta.elite == null) continue;
            meta.cooldown -= 10;
            if (meta.cooldown > 0) continue;

            Entity e = Bukkit.getEntity(en.getKey());
            if (!(e instanceof LivingEntity le) || !le.isValid()) continue;
            World w = le.getWorld();

            switch (meta.elite) {
                case TELEPORTER -> {
                    meta.cooldown = 100;
                    if (le instanceof Mob mob && mob.getTarget() instanceof Player t
                            && t.getWorld() == w && le.getLocation().distance(t.getLocation()) > 7) {
                        Location dest = t.getLocation().clone();
                        Vector dir = dest.getDirection().setY(0);
                        if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
                        dest.add(dir.normalize().multiply(-1.5));
                        if (!dest.getBlock().isEmpty() || !dest.clone().add(0, 1, 0).getBlock().isEmpty()) {
                            dest = t.getLocation().clone();
                        }
                        w.spawnParticle(Particle.WITCH, le.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
                        le.teleport(dest);
                        w.spawnParticle(Particle.WITCH, dest.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
                        w.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                    }
                }
                case POISONER -> {
                    meta.cooldown = 60;
                    boolean hit = false;
                    for (Player p : w.getPlayers()) {
                        if (!p.isDead() && p.getLocation().distanceSquared(le.getLocation()) <= 16) {
                            p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
                            hit = true;
                        }
                    }
                    if (hit) {
                        w.spawnParticle(Particle.WITCH, le.getLocation().add(0, 1, 0), 30, 1.5, 0.5, 1.5, 0.02);
                    }
                }
                case SUMMONER -> {
                    meta.cooldown = 200;
                    if (d.mobs.size() < 40) {
                        Room r = roomAt(d, le.getLocation());
                        for (int i = 0; i < 2; i++) {
                            Location loc = le.getLocation().clone().add(rnd.nextInt(5) - 2, 0, rnd.nextInt(5) - 2);
                            if (loc.getBlock().isEmpty() && loc.clone().add(0, 1, 0).getBlock().isEmpty()) {
                                plugin.mobs().spawn(d, r, loc, d.playersInside, null);
                            }
                        }
                        w.playSound(le.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 1f);
                    }
                }
                default -> meta.cooldown = 1000;
            }
        }
    }

    // ------------------------------------------------------------------
    //  Boss
    // ------------------------------------------------------------------

    private void spawnBoss(Dungeon d, Room r, List<Player> ps) {
        World w = d.world();
        if (w == null) return;
        int n = ps.size();

        BossType bt = BossType.values()[rnd.nextInt(BossType.values().length)];
        Location loc = new Location(w, r.centerX() + 0.5, r.floorY + 1, r.centerZ() + 0.5);
        Entity ent = w.spawnEntity(loc, bt.type);
        if (!(ent instanceof LivingEntity le)) {
            ent.remove();
            return;
        }

        d.bossSpawned = true;
        d.boss = le.getUniqueId();
        d.bossType = bt;
        d.phase = 1;
        d.abilityCd = 60;
        d.bossIdle = 0;

        double groupBoss = plugin.getConfig().getDouble("skalierung.pro-extra-spieler-boss-leben", 0.8);
        double max = Math.min(1900.0, d.difficulty.bossHealth * (1 + groupBoss * (n - 1)));
        d.bossMax = max;

        AttributeInstance hp = le.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(max);
            le.setHealth(max);
        }
        AttributeInstance scale = le.getAttribute(Attribute.SCALE);
        if (scale != null) scale.setBaseValue(bt.scale);
        AttributeInstance kb = le.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(0.7);
        AttributeInstance follow = le.getAttribute(Attribute.FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(48.0);

        if (le instanceof Zombie z) z.setBaby(false);
        if (le instanceof PiglinAbstract pa) pa.setImmuneToZombification(true);
        equipBoss(le, bt);

        le.setRemoveWhenFarAway(false);
        le.setPersistent(true);
        le.setCanPickupItems(false);
        le.setCustomName(DungeonQuest.c("&4&l" + bt.title));
        le.setCustomNameVisible(true);
        le.getPersistentDataContainer().set(plugin.dungeonKey(), PersistentDataType.INTEGER, d.id);
        tracked.put(le.getUniqueId(), d);
        if (le instanceof Mob m) m.setTarget(ps.get(rnd.nextInt(ps.size())));

        d.bar = Bukkit.createBossBar(DungeonQuest.c("&c&l" + bt.title + " &7(" + d.difficulty.colored() + "&7)"),
                bt.color, BarStyle.SEGMENTED_10);
        d.bar.setProgress(1.0);
        for (Player p : ps) {
            d.bar.addPlayer(p);
            p.sendTitle(DungeonQuest.c("&4&l" + bt.title), DungeonQuest.c("&7erwacht!"), 10, 50, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.7f);
        }
        w.strikeLightningEffect(loc);
    }

    private void equipBoss(LivingEntity le, BossType bt) {
        EntityEquipment eq = le.getEquipment();
        if (eq == null) return;
        if (bt == BossType.BONE_KING || bt == BossType.GIANT) {
            eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            eq.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            eq.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
            eq.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
        } else if (bt == BossType.LAVA_LORD) {
            eq.setHelmet(new ItemStack(Material.GOLDEN_HELMET));
            eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            eq.setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));
        }
    }

    private void bossTick(Dungeon d, List<Player> ps) {
        Entity ent = Bukkit.getEntity(d.boss);
        if (!(ent instanceof LivingEntity b) || !b.isValid() || b.isDead()) {
            softReset(d);
            return;
        }
        Room br = d.bossRoom();
        World w = b.getWorld();

        List<Player> inRoom = new ArrayList<>();
        for (Player p : ps) {
            if (br.contains(p.getLocation())) inRoom.add(p);
        }

        // Bossleiste
        if (d.bar != null) {
            d.bar.setProgress(Math.max(0.0, Math.min(1.0, b.getHealth() / d.bossMax)));
            for (Player p : ps) d.bar.addPlayer(p);
            for (Player p : new ArrayList<>(d.bar.getPlayers())) {
                if (!d.inside(p.getLocation())) d.bar.removePlayer(p);
            }
        }

        // Boss bleibt im Raum
        if (!br.contains(b.getLocation())) {
            b.teleport(new Location(w, br.centerX() + 0.5, br.floorY + 1, br.centerZ() + 0.5));
        }

        // Niemand mehr im Bossraum -> nach 30 Sekunden zuruecksetzen
        if (inRoom.isEmpty()) {
            d.bossIdle += 10;
            if (d.bossIdle >= 600) softReset(d);
            return;
        }
        d.bossIdle = 0;

        // Phasen
        double pct = b.getHealth() / d.bossMax;
        int ph = pct > 0.66 ? 1 : pct > 0.33 ? 2 : 3;
        if (ph != d.phase) {
            d.phase = ph;
            for (Player p : inRoom) {
                p.sendMessage(DungeonQuest.c("&4&l" + d.bossType.title + " &cwird wütend! &7(Phase " + ph + ")"));
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1f);
            }
            w.strikeLightningEffect(b.getLocation());
            if (ph == 3) {
                b.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 3600, 0, false, false));
            }
            useAbility(d, b, Ability.SUMMON, inRoom, br);
        }

        // Faehigkeiten
        d.abilityCd -= 10;
        if (d.abilityCd <= 0) {
            List<Ability> list = d.bossType.abilities;
            int avail = Math.min(list.size(), d.phase + 1);
            Ability a = list.get(rnd.nextInt(avail));
            useAbility(d, b, a, inRoom, br);
            int base = d.phase == 1 ? 160 : d.phase == 2 ? 120 : 90;
            d.abilityCd = (int) (base * (1.0 - 0.07 * (d.difficulty.tier - 1)));
        }
    }

    private void useAbility(Dungeon d, LivingEntity b, Ability a, List<Player> inRoom, Room br) {
        World w = b.getWorld();
        String title = d.bossType.title;
        switch (a) {
            case SLAM -> {
                Location c = b.getLocation().clone();
                tell(inRoom, "&c" + title + " holt zum Schlag aus! &7(Spring!)");
                ring(w, c, 6, Particle.CRIT);
                Bukkit.getScheduler().runTaskLater(plugin, () -> ring(w, c, 6, Particle.FLAME), 10L);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!b.isValid()) return;
                    w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 1);
                    w.playSound(c, Sound.ENTITY_WITHER_BREAK_BLOCK, 2f, 0.6f);
                    for (Player p : inRoom) {
                        if (!p.isOnline() || p.isDead() || p.getWorld() != w) continue;
                        if (p.getLocation().distance(c) > 6.5) continue;
                        if (p.getLocation().getY() > c.getY() + 1.4) continue; // gesprungen = ausgewichen
                        p.damage(6.0, b);
                        Vector v = p.getLocation().toVector().subtract(c.toVector()).setY(0);
                        if (v.lengthSquared() > 0.01) {
                            v = v.normalize().multiply(1.2).setY(0.6);
                        } else {
                            v = new Vector(0, 0.8, 0);
                        }
                        p.setVelocity(v);
                    }
                }, 20L);
            }
            case SUMMON -> {
                int cap = 8 + 3 * inRoom.size();
                if (d.mobs.size() >= cap) return;
                int count = 2 + d.difficulty.tier / 2 + inRoom.size();
                int n = Math.max(1, inRoom.size());
                for (int i = 0; i < count; i++) {
                    Location loc = b.getLocation().clone().add(rnd.nextInt(9) - 4, 0, rnd.nextInt(9) - 4);
                    if (loc.getBlock().isEmpty() && loc.clone().add(0, 1, 0).getBlock().isEmpty()) {
                        plugin.mobs().spawn(d, br, loc, n, inRoom.get(rnd.nextInt(inRoom.size())));
                    }
                }
                w.playSound(b.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.5f, 0.7f);
                tell(inRoom, "&5" + title + " ruft Verstärkung!");
            }
            case FIREBALLS -> {
                tell(inRoom, "&6" + title + " speit Feuerbälle!");
                int shots = 2 + d.difficulty.tier / 3;
                List<Player> targets = new ArrayList<>(inRoom);
                if (targets.size() > 4) targets = targets.subList(0, 4);
                for (Player t : targets) {
                    for (int i = 0; i < shots; i++) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (!b.isValid() || !t.isOnline() || t.isDead() || t.getWorld() != w) return;
                            Location from = b.getEyeLocation();
                            Vector dir = t.getEyeLocation().toVector().subtract(from.toVector());
                            if (dir.lengthSquared() < 0.01) return;
                            dir.normalize();
                            SmallFireball fb = w.spawn(from.clone().add(dir.clone().multiply(1.5)), SmallFireball.class);
                            fb.setShooter(b);
                            fb.setIsIncendiary(false);
                            fb.setYield(0f);
                            fb.setDirection(dir);
                            w.playSound(from, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);
                        }, 6L * i);
                    }
                }
            }
            case POISON_CLOUD -> {
                Player target = inRoom.get(rnd.nextInt(inRoom.size()));
                Location c = target.getLocation().clone();
                int amp = d.difficulty.tier >= 4 ? 1 : 0;
                tell(inRoom, "&2" + title + " beschwört eine Giftwolke!");
                new BukkitRunnable() {
                    int t = 0;

                    @Override
                    public void run() {
                        t++;
                        if (!b.isValid() || t > 16) {
                            cancel();
                            return;
                        }
                        w.spawnParticle(Particle.WITCH, c.clone().add(0, 0.5, 0), 30, 2.2, 0.4, 2.2, 0.02);
                        if (t > 4) {
                            for (Player p : inRoom) {
                                if (!p.isOnline() || p.isDead() || p.getWorld() != w) continue;
                                if (p.getLocation().distanceSquared(c) <= 12.25) {
                                    p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, amp));
                                    p.damage(1.5, b);
                                }
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0L, 10L);
            }
            case TELEPORT -> {
                Player t = inRoom.get(rnd.nextInt(inRoom.size()));
                Location tl = t.getLocation();
                Vector dir = tl.getDirection().setY(0);
                if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
                Location dest = tl.clone().add(dir.normalize().multiply(-2));
                if (!dest.getBlock().isEmpty() || !dest.clone().add(0, 1, 0).getBlock().isEmpty()) {
                    dest = tl.clone();
                }
                w.spawnParticle(Particle.WITCH, b.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.05);
                b.teleport(dest);
                w.spawnParticle(Particle.WITCH, dest.clone().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.05);
                w.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.7f);
                if (b instanceof Mob m) m.setTarget(t);
            }
            case LIGHTNING -> {
                tell(inRoom, "&e" + title + " ruft Blitze herab! &7(Bewegen!)");
                List<Player> pool = new ArrayList<>(inRoom);
                int strikes = Math.min(3, pool.size());
                for (int i = 0; i < strikes; i++) {
                    Player t = pool.remove(rnd.nextInt(pool.size()));
                    Location c = t.getLocation().clone();
                    ring(w, c, 2.5, Particle.CRIT);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!b.isValid()) return;
                        w.strikeLightningEffect(c);
                        for (Player p : inRoom) {
                            if (!p.isOnline() || p.isDead() || p.getWorld() != w) continue;
                            if (p.getLocation().distanceSquared(c) <= 7.3) {
                                p.damage(7.0, b);
                            }
                        }
                    }, 25L);
                }
            }
        }
    }

    private void ring(World w, Location c, double radius, Particle p) {
        for (int i = 0; i < 36; i++) {
            double a = Math.PI * 2 * i / 36.0;
            w.spawnParticle(p, c.getX() + Math.cos(a) * radius, c.getY() + 0.2, c.getZ() + Math.sin(a) * radius,
                    1, 0, 0, 0, 0);
        }
    }

    private void tell(List<Player> ps, String msg) {
        for (Player p : ps) p.sendMessage(DungeonQuest.c(msg));
    }

    // ------------------------------------------------------------------
    //  Sieg und Belohnung
    // ------------------------------------------------------------------

    private void victory(Dungeon d, Location at) {
        World w = d.world();
        if (w == null) return;
        long minutes = plugin.getConfig().getLong("respawn-minuten", 45);
        d.respawnAt = System.currentTimeMillis() + minutes * 60_000L;
        String bossTitle = d.bossType != null ? d.bossType.title : "Der Boss";
        d.boss = null;
        if (d.bar != null) {
            d.bar.removeAll();
            d.bar = null;
        }
        removeAllMobs(d);

        List<Player> winners = new ArrayList<>();
        for (Player p : w.getPlayers()) {
            if (!p.isDead() && d.inside(p.getLocation())) winners.add(p);
        }

        Difficulty df = d.difficulty;
        StringBuilder names = new StringBuilder();
        for (Player p : winners) {
            p.sendTitle(DungeonQuest.c("&6&lSIEG!"), DungeonQuest.c("&7" + bossTitle + " wurde besiegt"), 10, 70, 20);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            List<ItemStack> loot = plugin.loot().bossLoot(df);
            for (ItemStack it : loot) {
                Map<Integer, ItemStack> left = p.getInventory().addItem(it);
                for (ItemStack rest : left.values()) {
                    w.dropItemNaturally(p.getLocation(), rest);
                }
            }
            ItemStack rare = plugin.loot().rollRare(df);
            if (rare != null) {
                Map<Integer, ItemStack> left = p.getInventory().addItem(rare);
                for (ItemStack rest : left.values()) {
                    w.dropItemNaturally(p.getLocation(), rest);
                }
                String rn = rare.getItemMeta() != null ? rare.getItemMeta().getDisplayName() : "ein seltenes Item";
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.6f);
                Bukkit.broadcastMessage(DungeonQuest.PREFIX + DungeonQuest.c("&d&lSELTENER FUND! &6" + p.getName()
                        + " &7hat " + rn + " &7erbeutet!"));
            }
            p.giveExp(100 * df.tier);
            p.sendMessage(DungeonQuest.PREFIX + DungeonQuest.c("&aBelohnung erhalten! &7(" + loot.size() + " Gegenstände)"));
            if (names.length() > 0) names.append(", ");
            names.append(p.getName());
        }

        if (plugin.getConfig().getBoolean("broadcast-sieg", true) && names.length() > 0) {
            Bukkit.broadcastMessage(DungeonQuest.PREFIX + DungeonQuest.c("&6" + names + " &7besiegte &c" + bossTitle
                    + " &7im " + df.colored() + "&7-Dungeon!"));
        }

        for (int i = 0; i < 3; i++) {
            Firework fw = w.spawn(at.clone().add(rnd.nextInt(5) - 2, 1, rnd.nextInt(5) - 2), Firework.class);
            FireworkMeta fm = fw.getFireworkMeta();
            fm.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.ORANGE, Color.YELLOW)
                    .withFade(Color.RED)
                    .flicker(true)
                    .build());
            fm.setPower(1);
            fw.setFireworkMeta(fm);
        }
        save();
    }

    // ------------------------------------------------------------------
    //  Events
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Entity damager = e.getDamager();
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity s) damager = s;
        if (damager instanceof EvokerFangs f && f.getOwner() != null) damager = f.getOwner();

        Dungeon d = tracked.get(damager.getUniqueId());
        if (d == null) return;

        boolean isBoss = damager.getUniqueId().equals(d.boss);
        double base = isBoss ? d.difficulty.bossDamage : d.difficulty.mobDamage;
        double group = 1 + plugin.getConfig().getDouble("skalierung.pro-extra-spieler-schaden", 0.15)
                * (d.playersInside - 1);
        double phase = isBoss ? 1 + 0.2 * (d.phase - 1) : 1.0;
        e.setDamage(e.getDamage() * base * group * phase);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        Dungeon d = tracked.get(id);
        if (d == null) return;
        e.getDrops().clear();
        if (id.equals(d.boss)) {
            e.setDroppedExp(0);
            tracked.remove(id);
            victory(d, e.getEntity().getLocation());
        } else {
            e.setDroppedExp(3 + d.difficulty.tier * 3);
            removeMob(d, id);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (e.getPlayer().hasPermission("dungeonquest.admin")) return;
        for (Dungeon d : dungeons.values()) {
            if (d.protects(e.getBlock().getLocation())) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getPlayer().hasPermission("dungeonquest.admin")) return;
        for (Dungeon d : dungeons.values()) {
            if (d.protects(e.getBlock().getLocation())) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        for (Dungeon d : dungeons.values()) {
            if (d.protects(e.getLocation())) {
                e.blockList().clear();
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onNaturalSpawn(CreatureSpawnEvent e) {
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        if (r != CreatureSpawnEvent.SpawnReason.NATURAL
                && r != CreatureSpawnEvent.SpawnReason.JOCKEY
                && r != CreatureSpawnEvent.SpawnReason.MOUNT
                && r != CreatureSpawnEvent.SpawnReason.REINFORCEMENTS) return;
        for (Dungeon d : dungeons.values()) {
            if (d.inside(e.getLocation())) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (en.getPersistentDataContainer().has(plugin.dungeonKey(), PersistentDataType.INTEGER)
                    && !tracked.containsKey(en.getUniqueId())) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (en.isValid()) en.remove();
                });
            }
        }
    }
}
