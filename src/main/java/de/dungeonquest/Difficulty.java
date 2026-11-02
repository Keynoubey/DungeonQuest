package de.dungeonquest;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

public enum Difficulty {
    EASY("Leicht", ChatColor.GREEN, 1, 3, 4, 1.0, 1.0, 0.05, 150, 1.0),
    NORMAL("Normal", ChatColor.YELLOW, 2, 4, 5, 1.4, 1.25, 0.10, 280, 1.25),
    HARD("Schwer", ChatColor.GOLD, 3, 5, 6, 2.0, 1.6, 0.18, 450, 1.6),
    NIGHTMARE("Albtraum", ChatColor.RED, 4, 6, 7, 3.0, 2.0, 0.28, 700, 2.0),
    INFERNO("Inferno", ChatColor.DARK_RED, 5, 7, 8, 4.5, 2.5, 0.40, 900, 2.5);

    public final String display;
    public final ChatColor color;
    public final int tier;
    public int rooms;
    public int mobsPerRoom;
    public double mobHealth;
    public double mobDamage;
    public double eliteChance;
    public double bossHealth;
    public double bossDamage;

    Difficulty(String display, ChatColor color, int tier, int rooms, int mobsPerRoom,
               double mobHealth, double mobDamage, double eliteChance,
               double bossHealth, double bossDamage) {
        this.display = display;
        this.color = color;
        this.tier = tier;
        this.rooms = rooms;
        this.mobsPerRoom = mobsPerRoom;
        this.mobHealth = mobHealth;
        this.mobDamage = mobDamage;
        this.eliteChance = eliteChance;
        this.bossHealth = bossHealth;
        this.bossDamage = bossDamage;
    }

    public String colored() {
        return color + display;
    }

    public EntityType[] pool() {
        switch (this) {
            case EASY:
                return new EntityType[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER};
            case NORMAL:
                return new EntityType[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
                        EntityType.HUSK, EntityType.STRAY, EntityType.CAVE_SPIDER};
            case HARD:
                return new EntityType[]{EntityType.HUSK, EntityType.STRAY, EntityType.SKELETON,
                        EntityType.CAVE_SPIDER, EntityType.WITCH, EntityType.VINDICATOR, EntityType.PILLAGER};
            case NIGHTMARE:
                return new EntityType[]{EntityType.HUSK, EntityType.STRAY, EntityType.WITCH,
                        EntityType.VINDICATOR, EntityType.PILLAGER, EntityType.WITHER_SKELETON,
                        EntityType.PIGLIN_BRUTE};
            default:
                return new EntityType[]{EntityType.WITHER_SKELETON, EntityType.PIGLIN_BRUTE,
                        EntityType.VINDICATOR, EntityType.EVOKER, EntityType.BLAZE,
                        EntityType.WITCH, EntityType.STRAY};
        }
    }

    public void load(ConfigurationSection s) {
        if (s == null) return;
        rooms = Math.max(1, s.getInt("raeume", rooms));
        mobsPerRoom = Math.max(1, s.getInt("mobs-pro-raum", mobsPerRoom));
        mobHealth = s.getDouble("mob-leben", mobHealth);
        mobDamage = s.getDouble("mob-schaden", mobDamage);
        eliteChance = s.getDouble("elite-chance", eliteChance);
        bossHealth = s.getDouble("boss-leben", bossHealth);
        bossDamage = s.getDouble("boss-schaden", bossDamage);
    }

    public static void loadAll(FileConfiguration cfg) {
        for (Difficulty d : values()) {
            d.load(cfg.getConfigurationSection("schwierigkeiten." + d.name()));
        }
    }

    public static Difficulty parse(String s) {
        if (s == null) return null;
        String x = s.trim().toLowerCase();
        for (Difficulty d : values()) {
            if (d.name().toLowerCase().equals(x) || d.display.toLowerCase().equals(x)) return d;
        }
        if (x.equals("leicht")) return EASY;
        if (x.equals("schwer")) return HARD;
        if (x.equals("albtraum")) return NIGHTMARE;
        return null;
    }
}
