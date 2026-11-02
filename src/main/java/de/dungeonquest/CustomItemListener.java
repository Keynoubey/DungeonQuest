package de.dungeonquest;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

public final class CustomItemListener implements Listener {

    private final DungeonQuest plugin;
    private final LootFactory loot;
    private final NamespacedKey arrowKey;
    private final Random rnd = new Random();
    private boolean busy = false;

    public CustomItemListener(DungeonQuest plugin) {
        this.plugin = plugin;
        this.loot = plugin.loot();
        this.arrowKey = new NamespacedKey(plugin, "storm_arrow");

        // Phönix-Brustplatte: Passiveffekte
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if ("windstiefel".equals(loot.legendId(p.getInventory().getBoots()))) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, true, false));
                }
                if ("phoenix".equals(loot.legendId(p.getInventory().getChestplate()))) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0, true, false));
                    if (p.getHealth() < 12.0) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0, true, false));
                    }
                }
            }
        }, 40L, 40L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        if (!"sturmbogen".equals(loot.legendId(e.getBow()))) return;
        if (e.getProjectile() instanceof AbstractArrow arrow) {
            arrow.getPersistentDataContainer().set(arrowKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (busy) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        Entity damager = e.getDamager();

        if (damager instanceof Player p) {
            String id = loot.legendId(p.getInventory().getItemInMainHand());
            if ("blitzklinge".equals(id)) {
                if (rnd.nextDouble() < 0.25) {
                    victim.getWorld().strikeLightningEffect(victim.getLocation());
                    e.setDamage(e.getDamage() + 8.0);
                    busy = true;
                    try {
                        for (Entity n : victim.getNearbyEntities(3, 2, 3)) {
                            if (n instanceof Enemy && n instanceof LivingEntity le) {
                                le.damage(5.0, p);
                            }
                        }
                    } finally {
                        busy = false;
                    }
                }
            } else if ("seelensense".equals(id)) {
                AttributeInstance mh = p.getAttribute(Attribute.MAX_HEALTH);
                double max = mh != null ? mh.getValue() : 20.0;
                double heal = Math.max(1.0, e.getDamage() * 0.25);
                p.setHealth(Math.min(max, p.getHealth() + heal));
            }
        } else if (damager instanceof AbstractArrow arrow
                && arrow.getPersistentDataContainer().has(arrowKey, PersistentDataType.BYTE)) {
            victim.getWorld().strikeLightningEffect(victim.getLocation());
            e.setDamage(e.getDamage() + 5.0);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false));
        }
    }
}
