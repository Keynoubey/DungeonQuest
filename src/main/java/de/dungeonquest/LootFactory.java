package de.dungeonquest;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class LootFactory {

    public static final List<String> LEGENDARY_IDS = List.of("blitzklinge", "seelensense", "sturmbogen", "phoenix");

    public static final List<String> RARE_IDS = List.of(
            "windstiefel", "schwingen", "sturmdreizack", "titanenhammer",
            "ewigepicke", "aegis", "drachenei");

    private final DungeonQuest plugin;
    private final NamespacedKey legendKey;
    private final Random rnd = new Random();

    public LootFactory(DungeonQuest plugin) {
        this.plugin = plugin;
        this.legendKey = new NamespacedKey(plugin, "legendary");
    }

    /** Liefert die ID eines legendaeren Items oder null. */
    public String legendId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(legendKey, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------
    //  Raum-Truhen
    // ------------------------------------------------------------------

    public List<ItemStack> roomLoot(Difficulty df) {
        int t = df.tier;
        List<ItemStack> out = new ArrayList<>();
        int rolls = 3 + rnd.nextInt(3);
        for (int i = 0; i < rolls; i++) {
            switch (rnd.nextInt(10)) {
                case 0, 1 -> out.add(new ItemStack(Material.EMERALD, 1 + rnd.nextInt(2 + t)));
                case 2 -> out.add(new ItemStack(Material.GOLDEN_APPLE, 1 + rnd.nextInt(2)));
                case 3 -> out.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 3 + rnd.nextInt(4 * t)));
                case 4 -> out.add(new ItemStack(Material.COOKED_BEEF, 8 + rnd.nextInt(8)));
                case 5 -> out.add(new ItemStack(t >= 3 ? Material.DIAMOND : Material.IRON_INGOT, 1 + rnd.nextInt(3)));
                case 6 -> out.add(new ItemStack(Material.ARROW, 16 + rnd.nextInt(32)));
                case 7 -> {
                    if (t >= 2) out.add(new ItemStack(Material.ENDER_PEARL, 1 + rnd.nextInt(2)));
                    else out.add(new ItemStack(Material.GOLD_INGOT, 2 + rnd.nextInt(4)));
                }
                case 8 -> out.add(gear(Math.max(1, t - 1)));
                default -> {
                    if (t >= 4 && rnd.nextDouble() < 0.3) {
                        out.add(new ItemStack(Material.NETHERITE_SCRAP, 1 + rnd.nextInt(2)));
                    } else {
                        out.add(new ItemStack(Material.GOLDEN_CARROT, 8));
                    }
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    //  Boss-Belohnung (pro Spieler)
    // ------------------------------------------------------------------

    public List<ItemStack> bossLoot(Difficulty df) {
        int t = df.tier;
        List<ItemStack> out = new ArrayList<>();

        out.add(trophy(df));
        out.add(new ItemStack(Material.EMERALD, 6 * t + rnd.nextInt(6 * t)));
        out.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 8 + rnd.nextInt(8) * t));
        out.add(new ItemStack(Material.GOLDEN_APPLE, 2 + t));

        int gearCount = 1 + t / 2;
        for (int i = 0; i < gearCount; i++) out.add(gear(t));

        double[] legendChance = {0.06, 0.14, 0.28, 0.50, 1.0};
        if (rnd.nextDouble() < legendChance[t - 1]) {
            out.add(legendary(LEGENDARY_IDS.get(rnd.nextInt(LEGENDARY_IDS.size()))));
        }
        if (t >= 3 && rnd.nextDouble() < 0.4) out.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
        if (t >= 3 && rnd.nextDouble() < 0.3) out.add(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
        if (t >= 4) out.add(new ItemStack(Material.NETHERITE_INGOT, 1 + rnd.nextInt(t - 2)));
        if (t == 5) out.add(new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1));
        return out;
    }

    private ItemStack trophy(Difficulty df) {
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(DungeonQuest.c("&6Dungeon-Trophäe &7(" + df.colored() + "&7)"));
            List<String> lore = new ArrayList<>();
            lore.add(DungeonQuest.c("&8Beweis deines Sieges"));
            meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    // ------------------------------------------------------------------
    //  Verzauberte Ausruestung
    // ------------------------------------------------------------------

    public ItemStack gear(int tier) {
        String[] types = {"SWORD", "AXE", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "BOW"};
        String type = types[rnd.nextInt(types.length)];

        Material m;
        if (type.equals("BOW")) {
            m = Material.BOW;
        } else {
            String prefix = tier <= 1 ? "IRON" : tier <= 3 ? "DIAMOND" : "NETHERITE";
            m = Material.valueOf(prefix + "_" + type);
        }

        ItemStack item = new ItemStack(m);
        int lvl = Math.min(5, tier + 1);

        switch (type) {
            case "SWORD" -> {
                item.addUnsafeEnchantment(Enchantment.SHARPNESS, lvl);
                item.addUnsafeEnchantment(Enchantment.UNBREAKING, Math.min(3, tier + 1));
                if (tier >= 2) item.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, Math.min(2, tier - 1));
                if (tier >= 3) item.addUnsafeEnchantment(Enchantment.LOOTING, 3);
            }
            case "AXE" -> {
                item.addUnsafeEnchantment(Enchantment.SHARPNESS, lvl);
                item.addUnsafeEnchantment(Enchantment.UNBREAKING, Math.min(3, tier + 1));
            }
            case "BOW" -> {
                item.addUnsafeEnchantment(Enchantment.POWER, lvl);
                item.addUnsafeEnchantment(Enchantment.UNBREAKING, Math.min(3, tier + 1));
                if (tier >= 2) item.addUnsafeEnchantment(Enchantment.FLAME, 1);
            }
            default -> {
                item.addUnsafeEnchantment(Enchantment.PROTECTION, lvl);
                item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
                if (tier >= 3) item.addUnsafeEnchantment(Enchantment.THORNS, 2);
            }
        }
        if (tier >= 3) item.addUnsafeEnchantment(Enchantment.MENDING, 1);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(DungeonQuest.c("&6Dungeon-Beute: &f" + pretty(m)));
            List<String> lore = new ArrayList<>();
            lore.add(DungeonQuest.c("&7Aus einem Dungeon (Stufe " + tier + ")"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ------------------------------------------------------------------
    //  Legendaere Items
    // ------------------------------------------------------------------

    public ItemStack legendary(String id) {
        ItemStack item;
        String name;
        List<String> lore = new ArrayList<>();

        switch (id) {
            case "blitzklinge" -> {
                item = new ItemStack(Material.NETHERITE_SWORD);
                name = "&b&lBlitzklinge";
                item.addUnsafeEnchantment(Enchantment.SHARPNESS, 7);
                item.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 2);
                item.addUnsafeEnchantment(Enchantment.LOOTING, 4);
                lore.add("&7Mit 25 % Chance schlägt bei einem Treffer");
                lore.add("&7ein Blitz ein und verletzt Gegner in der Nähe.");
            }
            case "seelensense" -> {
                item = new ItemStack(Material.NETHERITE_AXE);
                name = "&5&lSeelensense";
                item.addUnsafeEnchantment(Enchantment.SHARPNESS, 8);
                item.addUnsafeEnchantment(Enchantment.LOOTING, 3);
                lore.add("&7Jeder Treffer heilt dich um einen");
                lore.add("&7Teil des verursachten Schadens.");
            }
            case "sturmbogen" -> {
                item = new ItemStack(Material.BOW);
                name = "&9&lSturmbogen";
                item.addUnsafeEnchantment(Enchantment.POWER, 8);
                item.addUnsafeEnchantment(Enchantment.FLAME, 1);
                lore.add("&7Pfeile schlagen wie ein Blitz ein,");
                lore.add("&7verursachen Extra-Schaden und lassen");
                lore.add("&7das Ziel leuchten.");
            }
            default -> {
                item = new ItemStack(Material.NETHERITE_CHESTPLATE);
                name = "&6&lPhönix-Brustplatte";
                item.addUnsafeEnchantment(Enchantment.PROTECTION, 6);
                item.addUnsafeEnchantment(Enchantment.THORNS, 4);
                lore.add("&7Schützt vor Feuer und Lava und");
                lore.add("&7regeneriert dich bei wenig Leben.");
                id = "phoenix";
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(DungeonQuest.c(name));
            List<String> colored = new ArrayList<>();
            colored.add(DungeonQuest.c("&6&oLegendär"));
            for (String l : lore) colored.add(DungeonQuest.c(l));
            meta.setLore(colored);
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(legendKey, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        return item;
    }


    // ------------------------------------------------------------------
    //  Seltene Spezial-Items (nur auf den schwersten Stufen)
    // ------------------------------------------------------------------

    /** Wuerfelt fuer einen Spieler, ob ein seltenes Item faellt. Gibt null zurueck, wenn nicht. */
    public ItemStack rollRare(Difficulty df) {
        double[] defaults = {0.0, 0.0, 0.02, 0.06, 0.12};
        double chance = plugin.getConfig().getDouble("seltene-items." + df.name(), defaults[df.ordinal()]);
        if (chance <= 0 || rnd.nextDouble() >= chance) return null;
        return rare(RARE_IDS.get(rnd.nextInt(RARE_IDS.size())));
    }

    public ItemStack rare(String id) {
        ItemStack item;
        String name;
        List<String> lore = new ArrayList<>();
        boolean unbreakable = true;

        switch (id) {
            case "windstiefel" -> {
                item = new ItemStack(Material.NETHERITE_BOOTS);
                name = "&b&lWindstiefel";
                item.addUnsafeEnchantment(Enchantment.PROTECTION, 6);
                item.addUnsafeEnchantment(Enchantment.FEATHER_FALLING, 8);
                item.addUnsafeEnchantment(Enchantment.DEPTH_STRIDER, 3);
                item.addUnsafeEnchantment(Enchantment.SOUL_SPEED, 3);
                lore.add("&7Verleiht dir dauerhaft Tempo II.");
            }
            case "schwingen" -> {
                item = new ItemStack(Material.ELYTRA);
                name = "&d&lSchwingen des Dungeons";
                item.addUnsafeEnchantment(Enchantment.UNBREAKING, 5);
                item.addUnsafeEnchantment(Enchantment.MENDING, 1);
                lore.add("&7Flügel aus den Tiefen. Unzerstörbar.");
            }
            case "sturmdreizack" -> {
                item = new ItemStack(Material.TRIDENT);
                name = "&3&lSturmdreizack";
                item.addUnsafeEnchantment(Enchantment.IMPALING, 10);
                item.addUnsafeEnchantment(Enchantment.LOYALTY, 5);
                item.addUnsafeEnchantment(Enchantment.CHANNELING, 1);
                lore.add("&7Kehrt immer zurück und ruft Blitze.");
            }
            case "titanenhammer" -> {
                item = new ItemStack(Material.MACE);
                name = "&c&lTitanenhammer";
                item.addUnsafeEnchantment(Enchantment.DENSITY, 8);
                item.addUnsafeEnchantment(Enchantment.BREACH, 4);
                item.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 2);
                lore.add("&7Je tiefer der Fall, desto verheerender.");
            }
            case "ewigepicke" -> {
                item = new ItemStack(Material.NETHERITE_PICKAXE);
                name = "&e&lEwige Spitzhacke";
                item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 10);
                item.addUnsafeEnchantment(Enchantment.FORTUNE, 5);
                lore.add("&7Baut blitzschnell ab und geht nie kaputt.");
            }
            case "aegis" -> {
                item = new ItemStack(Material.SHIELD);
                name = "&9&lAegis";
                item.addUnsafeEnchantment(Enchantment.UNBREAKING, 10);
                item.addUnsafeEnchantment(Enchantment.MENDING, 1);
                lore.add("&7Ein Schild, das nie zerbricht.");
            }
            default -> {
                item = new ItemStack(Material.DRAGON_EGG);
                name = "&5&lDrachenei";
                unbreakable = false;
                lore.add("&7Ein Sammlerstück aus den tiefsten Dungeons.");
                id = "drachenei";
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(DungeonQuest.c(name));
            List<String> colored = new ArrayList<>();
            colored.add(DungeonQuest.c("&d&oSelten"));
            for (String l : lore) colored.add(DungeonQuest.c(l));
            meta.setLore(colored);
            if (unbreakable) meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(legendKey, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String pretty(Material m) {
        String[] parts = m.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
