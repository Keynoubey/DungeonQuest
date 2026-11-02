package de.dungeonquest;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

public final class MobFactory {

    private static final Material[][] ARMOR = {
            {Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS},
            {Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS},
            {Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS},
            {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS},
            {Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS}
    };

    private static final Material[] SWORD = {
            Material.STONE_SWORD, Material.IRON_SWORD, Material.IRON_SWORD,
            Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
    };

    private final DungeonQuest plugin;
    private final Random rnd = new Random();

    public MobFactory(DungeonQuest plugin) {
        this.plugin = plugin;
    }

    /**
     * Spawnt einen Dungeon-Mob (evtl. Elite) und registriert ihn im Dungeon.
     */
    public LivingEntity spawn(Dungeon d, Dungeon.Room room, Location loc, int players, Player target) {
        World w = loc.getWorld();
        if (w == null) return null;
        Difficulty df = d.difficulty;

        EntityType[] pool = df.pool();
        EntityType type = pool[rnd.nextInt(pool.length)];
        Entity ent = w.spawnEntity(loc, type);
        if (!(ent instanceof LivingEntity le)) {
            ent.remove();
            return null;
        }

        double eliteChance = df.eliteChance + 0.04 * (players - 1);
        boolean elite = rnd.nextDouble() < eliteChance;
        Dungeon.EliteAbility ability = null;
        if (elite) {
            Dungeon.EliteAbility[] all = Dungeon.EliteAbility.values();
            ability = all[rnd.nextInt(all.length)];
        }

        // Leben
        double groupHp = plugin.getConfig().getDouble("skalierung.pro-extra-spieler-mob-leben", 0.6);
        AttributeInstance hp = le.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            double max = hp.getBaseValue() * df.mobHealth * (1 + groupHp * (players - 1));
            if (elite) max *= 1.5;
            if (ability == Dungeon.EliteAbility.BRUTE) max *= 1.8;
            max = Math.min(max, 1000.0);
            hp.setBaseValue(max);
            le.setHealth(max);
        }

        equip(le, type, Math.min(5, df.tier + (elite ? 1 : 0)));

        if (le instanceof Zombie z) z.setBaby(false);
        if (le instanceof PiglinAbstract pa) pa.setImmuneToZombification(true);

        le.setRemoveWhenFarAway(false);
        le.setPersistent(true);
        le.setCanPickupItems(false);

        if (elite) {
            le.setGlowing(true);
            le.setCustomName(DungeonQuest.c("&6\u2726 &c" + pretty(type) + " &7[" + ability.title + "]"));
            le.setCustomNameVisible(true);
            if (ability == Dungeon.EliteAbility.BERSERKER) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 3600, 1, false, false));
            } else if (ability == Dungeon.EliteAbility.BRUTE) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 3600, 1, false, false));
            }
        }

        if (le instanceof Mob m && target != null) {
            m.setTarget(target);
        }

        plugin.manager().register(d, room, le, new Dungeon.MobMeta(ability));
        w.spawnParticle(Particle.SMOKE, loc.clone().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
        return le;
    }

    private void equip(LivingEntity le, EntityType t, int tier) {
        EntityEquipment eq = le.getEquipment();
        if (eq == null) return;

        boolean armored = t == EntityType.ZOMBIE || t == EntityType.HUSK || t == EntityType.SKELETON
                || t == EntityType.STRAY || t == EntityType.WITHER_SKELETON || t == EntityType.PIGLIN_BRUTE
                || t == EntityType.VINDICATOR || t == EntityType.PILLAGER;
        if (armored && rnd.nextDouble() < 0.45 + 0.1 * tier) {
            Material[] a = ARMOR[tier - 1];
            eq.setHelmet(new ItemStack(a[0]));
            eq.setChestplate(new ItemStack(a[1]));
            eq.setLeggings(new ItemStack(a[2]));
            eq.setBoots(new ItemStack(a[3]));
        }
        if (t == EntityType.ZOMBIE || t == EntityType.HUSK || t == EntityType.WITHER_SKELETON) {
            eq.setItemInMainHand(new ItemStack(SWORD[tier - 1]));
        }
    }

    private String pretty(EntityType t) {
        String[] parts = t.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
