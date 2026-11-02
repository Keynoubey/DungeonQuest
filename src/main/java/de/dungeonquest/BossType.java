package de.dungeonquest;

import org.bukkit.boss.BarColor;
import org.bukkit.entity.EntityType;

import java.util.List;

public enum BossType {
    BONE_KING("Der Knochenkönig", EntityType.WITHER_SKELETON, BarColor.WHITE, 1.7,
            List.of(Ability.SUMMON, Ability.SLAM, Ability.FIREBALLS, Ability.LIGHTNING)),
    PLAGUE_QUEEN("Die Seuchenkönigin", EntityType.EVOKER, BarColor.GREEN, 1.6,
            List.of(Ability.POISON_CLOUD, Ability.TELEPORT, Ability.SUMMON, Ability.LIGHTNING)),
    LAVA_LORD("Der Lavaherr", EntityType.PIGLIN_BRUTE, BarColor.RED, 1.8,
            List.of(Ability.FIREBALLS, Ability.SLAM, Ability.TELEPORT, Ability.SUMMON)),
    GIANT("Der Verdorbene Riese", EntityType.ZOMBIE, BarColor.PURPLE, 3.0,
            List.of(Ability.SLAM, Ability.SUMMON, Ability.POISON_CLOUD, Ability.LIGHTNING));

    public enum Ability {
        SLAM, SUMMON, FIREBALLS, POISON_CLOUD, TELEPORT, LIGHTNING
    }

    public final String title;
    public final EntityType type;
    public final BarColor color;
    public final double scale;
    public final List<Ability> abilities;

    BossType(String title, EntityType type, BarColor color, double scale, List<Ability> abilities) {
        this.title = title;
        this.type = type;
        this.color = color;
        this.scale = scale;
        this.abilities = abilities;
    }
}
