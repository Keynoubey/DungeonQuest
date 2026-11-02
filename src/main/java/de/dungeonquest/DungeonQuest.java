package de.dungeonquest;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class DungeonQuest extends JavaPlugin {

    public static final String PREFIX = c("&8[&6Dungeon&8] &7");

    private DungeonManager manager;
    private DungeonBuilder builder;
    private MobFactory mobFactory;
    private LootFactory lootFactory;
    private NamespacedKey dungeonKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Difficulty.loadAll(getConfig());

        dungeonKey = new NamespacedKey(this, "dungeon_id");
        lootFactory = new LootFactory(this);
        builder = new DungeonBuilder(this);
        mobFactory = new MobFactory(this);
        manager = new DungeonManager(this);
        manager.load();

        getServer().getPluginManager().registerEvents(manager, this);
        getServer().getPluginManager().registerEvents(new CustomItemListener(this), this);

        DungeonCommand cmd = new DungeonCommand(this);
        PluginCommand pc = getCommand("dungeon");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }

        manager.start();
        getServer().getScheduler().runTaskLater(this, manager::autoGenerateIfNeeded, 100L);
        getLogger().info("DungeonQuest aktiv - " + manager.all().size() + " Dungeons geladen.");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }

    public DungeonManager manager() { return manager; }
    public DungeonBuilder builder() { return builder; }
    public MobFactory mobs() { return mobFactory; }
    public LootFactory loot() { return lootFactory; }
    public NamespacedKey dungeonKey() { return dungeonKey; }

    public static String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
