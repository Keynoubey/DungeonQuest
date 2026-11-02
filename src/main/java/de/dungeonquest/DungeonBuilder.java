package de.dungeonquest;

import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public final class DungeonBuilder {

    private static final Material[][] PALETTE = {
            {Material.STONE_BRICKS, Material.STONE_BRICKS, Material.STONE_BRICKS,
                    Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS},
            {Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
                    Material.CRACKED_STONE_BRICKS, Material.MOSSY_COBBLESTONE},
            {Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_BRICKS, Material.CRACKED_DEEPSLATE_BRICKS,
                    Material.DEEPSLATE_TILES, Material.COBBLED_DEEPSLATE},
            {Material.POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BLACKSTONE_BRICKS,
                    Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, Material.BLACKSTONE},
            {Material.NETHER_BRICKS, Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS,
                    Material.CRACKED_NETHER_BRICKS, Material.BLACKSTONE}
    };

    private final DungeonQuest plugin;
    private final Random rnd = new Random();

    public DungeonBuilder(DungeonQuest plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Platzierung
    // ------------------------------------------------------------------

    /**
     * Prueft den Platz und baut den Dungeon, wenn er passt.
     * strict = true: ablehnen bei Wasser oder zu unebenem Gelaende.
     */
    public Dungeon tryPlace(World w, Difficulty df, int x, int z, int id, boolean strict) {
        if (w.getEnvironment() != World.Environment.NORMAL) return null;

        int len = 5 + df.rooms * 16 + 26;

        // Erst nur den Eingang pruefen (guenstig), dann das ganze Gelaende
        Block origin = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (strict && origin.isLiquid()) return null;

        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;
        for (int dx = -6; dx <= len; dx += 16) {
            for (int dz = -12; dz <= 12; dz += 12) {
                Block top = w.getHighestBlockAt(x + dx, z + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (strict && top.isLiquid()) return null;
                minH = Math.min(minH, top.getY());
                maxH = Math.max(maxH, top.getY());
            }
        }
        if (strict && maxH - minH > 20) return null;

        int floorY = minH - 11;
        if (floorY < Math.max(w.getMinHeight() + 8, -30)) return null;

        int surfaceY = Integer.MIN_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                surfaceY = Math.max(surfaceY,
                        w.getHighestBlockYAt(x + dx, z + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES));
            }
        }

        Dungeon d = new Dungeon(id, w.getName(), x, z, floorY, surfaceY, df, Dungeon.Kind.UNDERGROUND);
        build(d);
        return d;
    }

    // ------------------------------------------------------------------
    //  Bau
    // ------------------------------------------------------------------

    public void build(Dungeon d) {
        World w = d.world();
        if (w == null) return;
        if (d.kind == Dungeon.Kind.TOWER) {
            buildTower(w, d);
            return;
        }

        for (Dungeon.Room r : d.rooms) shell(w, d, r);
        for (Dungeon.Room r : d.rooms) air(w, r);
        entrance(w, d);
        decorate(w, d);
        refillChests(d);
    }

    private void shell(World w, Dungeon d, Dungeon.Room r) {
        int ex = r.type == Dungeon.RoomType.CORRIDOR ? 0 : 1;
        fillWall(w, d.difficulty, r.minX - ex, r.floorY, r.minZ - 1,
                r.maxX + ex, r.floorY + r.height + 1, r.maxZ + 1);
    }

    private void air(World w, Dungeon.Room r) {
        for (int x = r.minX; x <= r.maxX; x++) {
            for (int y = r.floorY + 1; y <= r.floorY + r.height; y++) {
                for (int z = r.minZ; z <= r.maxZ; z++) {
                    set(w, x, y, z, Material.AIR);
                }
            }
        }
    }

    private void entrance(World w, Dungeon d) {
        int ox = d.ox;
        int oz = d.oz;
        int fy = d.floorY;
        int sy = d.surfaceY;
        Difficulty df = d.difficulty;

        // Turmwand ueber dem Startraum
        for (int y = fy + 7; y <= sy + 3; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                        set(w, ox + dx, y, oz + dz, wall(df));
                    } else {
                        set(w, ox + dx, y, oz + dz, Material.AIR);
                    }
                }
            }
        }
        // Schacht durch die Decke und ueber den Turm hinaus freiraeumen
        for (int y = fy + 6; y <= sy + 6; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    set(w, ox + dx, y, oz + dz, Material.AIR);
                }
            }
        }
        // Zinnen
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if ((Math.abs(dx) == 2 || Math.abs(dz) == 2) && (dx + dz) % 2 == 0) {
                    set(w, ox + dx, sy + 4, oz + dz, wall(df));
                }
            }
        }
        // Plattform oben (ostliche zwei Reihen), Loch bleibt an der Westseite
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(w, ox + dx, sy, oz + dz, wall(df));
            }
        }
        // Eingang an der Ostseite
        set(w, ox + 2, sy + 1, oz, Material.AIR);
        set(w, ox + 2, sy + 2, oz, Material.AIR);
        // Vorplatz
        for (int px = ox + 3; px <= ox + 5; px++) {
            for (int pz = oz - 1; pz <= oz + 1; pz++) {
                set(w, px, sy, pz, wall(df));
                for (int y = sy + 1; y <= sy + 3; y++) set(w, px, y, pz, Material.AIR);
                for (int y = sy - 1; y >= sy - 10; y--) {
                    Block b = w.getBlockAt(px, y, pz);
                    if (b.getType().isAir() || b.isLiquid()) {
                        b.setType(wall(df), false);
                    } else {
                        break;
                    }
                }
            }
        }
        set(w, ox + 1, sy + 1, oz + 1, Material.TORCH);

        // Becken unten (faengt den Sturz ab) mit Rand
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    set(w, ox + dx, fy + 1, oz + dz, wall(df));
                } else {
                    set(w, ox + dx, fy + 1, oz + dz, Material.WATER);
                }
            }
        }
        // Stuetzsaeule fuer die Leiter
        for (int y = fy + 1; y <= fy + 5; y++) {
            set(w, ox - 2, y, oz, wall(df));
        }
        // Leiter
        for (int y = fy + 1; y <= sy + 1; y++) {
            Block b = w.getBlockAt(ox - 1, y, oz);
            b.setType(Material.LADDER, false);
            BlockData bd = b.getBlockData();
            if (bd instanceof Directional dir) {
                dir.setFacing(BlockFace.EAST);
                b.setBlockData(dir, false);
            }
        }
    }

    private void decorate(World w, Dungeon d) {
        Difficulty df = d.difficulty;
        for (Dungeon.Room r : d.rooms) {
            int fy = r.floorY;
            switch (r.type) {
                case START -> {
                    torch(w, r.minX, fy + 1, r.minZ, false);
                    torch(w, r.minX, fy + 1, r.maxZ, false);
                    torch(w, r.maxX, fy + 1, r.minZ, false);
                    torch(w, r.maxX, fy + 1, r.maxZ, false);
                }
                case CORRIDOR -> torch(w, (r.minX + r.maxX) / 2, fy + 1, r.minZ, false);
                case COMBAT -> {
                    int cx = r.centerX();
                    int cz = d.oz;
                    int[][] pillars = {{-3, -3}, {-3, 3}, {3, -3}, {3, 3}};
                    for (int[] p : pillars) {
                        for (int y = fy + 1; y <= fy + r.height; y++) {
                            set(w, cx + p[0], y, cz + p[1], wall(df));
                        }
                    }
                    torch(w, r.minX, fy + 1, r.minZ, false);
                    torch(w, r.minX, fy + 1, r.maxZ, false);
                    torch(w, r.maxX, fy + 1, r.minZ, false);
                    torch(w, r.maxX, fy + 1, r.maxZ, false);
                    torch(w, cx, fy + 1, r.minZ, false);
                    torch(w, cx, fy + 1, r.maxZ, false);
                    if (df.tier <= 3) {
                        for (int i = 0; i < 6; i++) {
                            int wx = r.minX + rnd.nextInt(r.maxX - r.minX + 1);
                            int wz = r.minZ + rnd.nextInt(r.maxZ - r.minZ + 1);
                            Block b = w.getBlockAt(wx, fy + r.height, wz);
                            if (b.getType().isAir()) b.setType(Material.COBWEB, false);
                        }
                    }
                }
                case BOSS -> {
                    int cx = r.centerX();
                    int cz = d.oz;
                    int[][] pillars = {{-6, -6}, {-6, 6}, {6, -6}, {6, 6}, {-6, 0}, {6, 0}, {0, -6}, {0, 6}};
                    for (int[] p : pillars) {
                        for (int y = fy + 1; y <= fy + r.height; y++) {
                            set(w, cx + p[0], y, cz + p[1], wall(df));
                        }
                    }
                    torch(w, r.minX, fy + 1, r.minZ, true);
                    torch(w, r.minX, fy + 1, r.maxZ, true);
                    torch(w, r.maxX, fy + 1, r.minZ, true);
                    torch(w, r.maxX, fy + 1, r.maxZ, true);
                    for (int x = r.minX + 2; x < r.maxX; x += 4) {
                        torch(w, x, fy + 1, r.minZ, true);
                        torch(w, x, fy + 1, r.maxZ, true);
                    }
                }
            }
        }
    }


    // ------------------------------------------------------------------
    //  Tuerme an der Oberflaeche
    // ------------------------------------------------------------------

    public Dungeon tryPlaceTower(World w, Difficulty df, int x, int z, int id, boolean strict) {
        if (w.getEnvironment() != World.Environment.NORMAL) return null;

        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;
        for (int dx = -9; dx <= 9; dx += 9) {
            for (int dz = -9; dz <= 9; dz += 9) {
                Block top = w.getHighestBlockAt(x + dx, z + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (strict && top.isLiquid()) return null;
                minH = Math.min(minH, top.getY());
                maxH = Math.max(maxH, top.getY());
            }
        }
        if (strict && maxH - minH > 8) return null;

        int base = maxH;
        int floors = df.rooms + 2;
        if (base + floors * 6 + 12 >= w.getMaxHeight()) return null;
        if (base < w.getMinHeight() + 20) return null;

        Dungeon d = new Dungeon(id, w.getName(), x, z, base, base, df, Dungeon.Kind.TOWER);
        build(d);
        return d;
    }

    private void buildTower(World w, Dungeon d) {
        int ox = d.ox;
        int oz = d.oz;
        int base = d.floorY;
        Difficulty df = d.difficulty;
        int n = d.rooms.size();

        // Mauern, Boeden und Decken
        for (Dungeon.Room r : d.rooms) {
            fillWall(w, df, r.minX - 1, r.floorY, r.minZ - 1, r.maxX + 1, r.floorY + r.height + 1, r.maxZ + 1);
        }
        // Fundament bis zum Boden
        for (int x = ox - 8; x <= ox + 8; x++) {
            for (int z = oz - 8; z <= oz + 8; z++) {
                for (int y = base - 1; y >= base - 15; y--) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType().isAir() || b.isLiquid()) {
                        b.setType(wall(df), false);
                    } else {
                        break;
                    }
                }
            }
        }
        // Innenraeume
        for (Dungeon.Room r : d.rooms) air(w, r);

        // Leitern zwischen den Etagen (Ostseite)
        for (int i = 0; i < n - 1; i++) {
            Dungeon.Room low = d.rooms.get(i);
            int fy = low.floorY;
            set(w, ox + 7, fy + 6, oz, Material.AIR);
            for (int y = fy + 1; y <= fy + 7; y++) {
                Block b = w.getBlockAt(ox + 7, y, oz);
                b.setType(Material.LADDER, false);
                BlockData bd = b.getBlockData();
                if (bd instanceof Directional dir) {
                    dir.setFacing(BlockFace.WEST);
                    b.setBlockData(dir, false);
                }
            }
        }

        // Eingangstuer (Westseite) und Vorplatz
        set(w, ox - 8, base + 1, oz, Material.AIR);
        set(w, ox - 8, base + 2, oz, Material.AIR);
        for (int px = ox - 11; px <= ox - 9; px++) {
            for (int pz = oz - 1; pz <= oz + 1; pz++) {
                set(w, px, base, pz, wall(df));
                for (int y = base + 1; y <= base + 3; y++) set(w, px, y, pz, Material.AIR);
                for (int y = base - 1; y >= base - 10; y--) {
                    Block b = w.getBlockAt(px, y, pz);
                    if (b.getType().isAir() || b.isLiquid()) {
                        b.setType(wall(df), false);
                    } else {
                        break;
                    }
                }
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int y = base; y <= base + 3; y++) set(w, ox - 9, y, oz + 2 * side, wall(df));
            set(w, ox - 9, base + 4, oz + 2 * side, Material.TORCH);
        }

        // Dach mit Zinnen
        Dungeon.Room top = d.bossRoom();
        int roofY = top.floorY + top.height + 1;
        for (int x = ox - 8; x <= ox + 8; x++) {
            for (int z = oz - 8; z <= oz + 8; z++) {
                boolean edge = x == ox - 8 || x == ox + 8 || z == oz - 8 || z == oz + 8;
                if (edge && (x + z) % 2 == 0) set(w, x, roofY + 1, z, wall(df));
            }
        }

        // Dekoration
        for (Dungeon.Room r : d.rooms) {
            int fy = r.floorY;
            switch (r.type) {
                case START -> {
                    torch(w, r.minX, fy + 1, r.minZ, false);
                    torch(w, r.minX, fy + 1, r.maxZ, false);
                    torch(w, r.maxX, fy + 1, r.minZ, false);
                    torch(w, r.maxX, fy + 1, r.maxZ, false);
                }
                case COMBAT -> {
                    int[][] pillars = {{-3, -3}, {-3, 3}, {3, -3}, {3, 3}};
                    for (int[] p : pillars) {
                        for (int y = fy + 1; y <= fy + r.height; y++) {
                            set(w, ox + p[0], y, oz + p[1], wall(df));
                        }
                    }
                    torch(w, r.minX, fy + 1, r.minZ, false);
                    torch(w, r.minX, fy + 1, r.maxZ, false);
                    torch(w, r.maxX, fy + 1, r.minZ, false);
                    torch(w, ox, fy + 1, r.minZ, false);
                    torch(w, ox, fy + 1, r.maxZ, false);
                    torch(w, r.minX, fy + 1, oz, false);
                    if (df.tier <= 3) {
                        for (int i = 0; i < 5; i++) {
                            int wx = r.minX + rnd.nextInt(r.maxX - r.minX + 1);
                            int wz = r.minZ + rnd.nextInt(r.maxZ - r.minZ + 1);
                            Block b = w.getBlockAt(wx, fy + r.height, wz);
                            if (b.getType().isAir()) b.setType(Material.COBWEB, false);
                        }
                    }
                }
                case BOSS -> {
                    int[][] pillars = {{-5, -5}, {-5, 5}, {5, -5}, {5, 5}};
                    for (int[] p : pillars) {
                        for (int y = fy + 1; y <= fy + r.height; y++) {
                            set(w, ox + p[0], y, oz + p[1], wall(df));
                        }
                    }
                    torch(w, r.minX, fy + 1, r.minZ, true);
                    torch(w, r.minX, fy + 1, r.maxZ, true);
                    torch(w, r.maxX, fy + 1, r.minZ, true);
                    torch(w, r.maxX, fy + 1, r.maxZ, true);
                    for (int x = r.minX + 2; x < r.maxX; x += 4) {
                        torch(w, x, fy + 1, r.minZ, true);
                        torch(w, x, fy + 1, r.maxZ, true);
                    }
                }
                default -> { }
            }
        }
        refillChests(d);
    }

    // ------------------------------------------------------------------
    //  Truhen
    // ------------------------------------------------------------------

    public void refillChests(Dungeon d) {
        World w = d.world();
        if (w == null) return;
        for (int[] c : d.chests) {
            Block b = w.getBlockAt(c[0], c[1], c[2]);
            if (b.getType() != Material.CHEST) {
                b.setType(Material.CHEST, false);
                BlockData bd = b.getBlockData();
                if (bd instanceof Directional dir) {
                    dir.setFacing(BlockFace.NORTH);
                    b.setBlockData(dir, false);
                }
            }
            BlockState st = b.getState();
            if (st instanceof Chest chest) {
                Inventory inv = chest.getBlockInventory();
                inv.clear();
                for (ItemStack it : plugin.loot().roomLoot(d.difficulty)) {
                    inv.addItem(it);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Helfer
    // ------------------------------------------------------------------

    private Material wall(Difficulty df) {
        Material[] pal = PALETTE[df.tier - 1];
        return pal[rnd.nextInt(pal.length)];
    }

    private void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }

    private void torch(World w, int x, int y, int z, boolean soul) {
        set(w, x, y, z, soul ? Material.SOUL_TORCH : Material.TORCH);
    }

    private void fillWall(World w, Difficulty df, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    set(w, x, y, z, wall(df));
                }
            }
        }
    }
}
