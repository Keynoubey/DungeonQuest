package de.dungeonquest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BossBar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Dungeon {

    public enum RoomType { START, CORRIDOR, COMBAT, BOSS }

    public enum Kind { UNDERGROUND, TOWER }

    public enum EliteAbility {
        TELEPORTER("Teleporter"),
        POISONER("Giftspucker"),
        SUMMONER("Beschwörer"),
        BERSERKER("Berserker"),
        BRUTE("Koloss");

        public final String title;

        EliteAbility(String title) {
            this.title = title;
        }
    }

    public static final class MobMeta {
        public final EliteAbility elite;
        public int cooldown = 60;

        public MobMeta(EliteAbility elite) {
            this.elite = elite;
        }
    }

    public static final class Room {
        public final RoomType type;
        public final int minX, maxX, minZ, maxZ, floorY, height;
        public boolean active;
        public boolean cleared;
        public final Set<UUID> mobs = new HashSet<>();

        public Room(RoomType type, int minX, int maxX, int minZ, int maxZ, int floorY, int height) {
            this.type = type;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.floorY = floorY;
            this.height = height;
        }

        public int centerX() {
            return (minX + maxX) / 2;
        }

        public int centerZ() {
            return (minZ + maxZ) / 2;
        }

        public boolean contains(Location l) {
            int x = l.getBlockX();
            int y = l.getBlockY();
            int z = l.getBlockZ();
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ
                    && y >= floorY && y <= floorY + height + 1;
        }
    }

    // ---- feste Daten (werden gespeichert) ----
    public final int id;
    public final String worldName;
    public final int ox, oz, floorY, surfaceY;
    public final Difficulty difficulty;
    public final Kind kind;

    // ---- abgeleitete Daten (Layout) ----
    public final List<Room> rooms = new ArrayList<>();
    public final List<int[]> chests = new ArrayList<>();
    public int minX, maxX, minZ, maxZ;
    public int topY;

    // ---- Laufzeit-Zustand ----
    public final Map<UUID, MobMeta> mobs = new HashMap<>();
    public UUID boss;
    public BossType bossType;
    public BossBar bar;
    public double bossMax = 1;
    public int phase = 1;
    public boolean bossSpawned;
    public long respawnAt;
    public int abilityCd = 60;
    public int idleTicks;
    public int bossIdle;
    public int playersInside = 1;
    public boolean needsRefill;

    public Dungeon(int id, String worldName, int ox, int oz, int floorY, int surfaceY, Difficulty difficulty, Kind kind) {
        this.id = id;
        this.worldName = worldName;
        this.ox = ox;
        this.oz = oz;
        this.floorY = floorY;
        this.surfaceY = surfaceY;
        this.difficulty = difficulty;
        this.kind = kind;
        if (kind == Kind.TOWER) layoutTower(); else layout();
    }

    private void layout() {
        int h = 5;
        rooms.add(new Room(RoomType.START, ox - 4, ox + 4, oz - 4, oz + 4, floorY, h));
        int cursor = ox + 5;
        for (int i = 0; i < difficulty.rooms; i++) {
            rooms.add(new Room(RoomType.CORRIDOR, cursor, cursor + 4, oz - 1, oz + 1, floorY, 3));
            Room r = new Room(RoomType.COMBAT, cursor + 5, cursor + 15, oz - 5, oz + 5, floorY, h);
            rooms.add(r);
            chests.add(new int[]{r.maxX - 1, floorY + 1, r.maxZ});
            cursor += 16;
        }
        rooms.add(new Room(RoomType.CORRIDOR, cursor, cursor + 4, oz - 1, oz + 1, floorY, 3));
        rooms.add(new Room(RoomType.BOSS, cursor + 5, cursor + 25, oz - 10, oz + 10, floorY, 7));
        minX = ox - 5;
        maxX = cursor + 26;
        minZ = oz - 11;
        maxZ = oz + 11;
    }

    private void layoutTower() {
        int floors = difficulty.rooms + 2;
        for (int i = 0; i < floors; i++) {
            int fy = floorY + 6 * i;
            RoomType t = i == 0 ? RoomType.START : i == floors - 1 ? RoomType.BOSS : RoomType.COMBAT;
            int h = i == floors - 1 ? 7 : 5;
            Room r = new Room(t, ox - 7, ox + 7, oz - 7, oz + 7, fy, h);
            rooms.add(r);
            if (t == RoomType.COMBAT) chests.add(new int[]{ox + 7, fy + 1, oz + 7});
        }
        minX = ox - 8;
        maxX = ox + 8;
        minZ = oz - 8;
        maxZ = oz + 8;
        topY = floorY + 6 * (floors - 1) + 10;
    }

    /** Ort vor dem Eingang (zum Teleportieren). */
    public Location entrance() {
        World w = world();
        if (kind == Kind.TOWER) return new Location(w, ox - 10.5, surfaceY + 1, oz + 0.5, -90f, 0f);
        return new Location(w, ox + 6.5, surfaceY + 1, oz + 0.5, 90f, 0f);
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    /** Unterirdischer Bereich (dort zaehlen Spieler als "im Dungeon"). */
    public boolean inside(Location l) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(worldName)) return false;
        int x = l.getBlockX();
        int y = l.getBlockY();
        int z = l.getBlockZ();
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ
                && y >= floorY - 1 && y <= (kind == Kind.TOWER ? topY : floorY + 9);
    }

    /** Geschuetzter Bereich: Dungeon plus Eingangsturm und Schacht. */
    public boolean protects(Location l) {
        if (inside(l)) return true;
        if (kind == Kind.TOWER) {
            if (l.getWorld() == null || !l.getWorld().getName().equals(worldName)) return false;
            return l.getBlockX() >= minX - 1 && l.getBlockX() <= maxX + 1 && l.getBlockZ() >= minZ - 1
                    && l.getBlockZ() <= maxZ + 1 && l.getBlockY() >= floorY - 15 && l.getBlockY() <= topY + 2;
        }
        if (l.getWorld() == null || !l.getWorld().getName().equals(worldName)) return false;
        int x = l.getBlockX();
        int y = l.getBlockY();
        int z = l.getBlockZ();
        return x >= ox - 3 && x <= ox + 6 && z >= oz - 3 && z <= oz + 3
                && y >= floorY && y <= surfaceY + 8;
    }

    public Room bossRoom() {
        for (Room r : rooms) {
            if (r.type == RoomType.BOSS) return r;
        }
        return null;
    }
}
