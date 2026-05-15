package com.herefishy.herefishy.config;

import com.herefishy.herefishy.HereFishyPlugin;
import com.herefishy.herefishy.session.ChestRoute;
import com.herefishy.herefishy.session.DepositBucket;
import com.herefishy.herefishy.session.FishySession;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class PlayerConfigManager {

    private final HereFishyPlugin plugin;
    private final File userDataFolder;

    public PlayerConfigManager(HereFishyPlugin plugin) {
        this.plugin = plugin;
        this.userDataFolder = new File(plugin.getDataFolder(), "userdata");
        if (!userDataFolder.exists()) {
            userDataFolder.mkdirs();
        }
    }

    public void saveSession(UUID uuid, FishySession session) {
        File file = new File(userDataFolder, uuid.toString() + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        // Save overrides
        ConfigurationSection overridesSection = config.createSection("overrides");
        for (Map.Entry<Material, DepositBucket> entry : session.getTreasureJunkOverridesView().entrySet()) {
            overridesSection.set(entry.getKey().name(), entry.getValue().name());
        }

        // Save chest routes
        if (session.getTreasureChest() != null) {
            saveChestRoute(config.createSection("treasureChest"), session.getTreasureChest());
        }
        if (session.getFishChest() != null) {
            saveChestRoute(config.createSection("fishChest"), session.getFishChest());
        }
        if (session.getJunkStand() != null) {
            saveLocation(config.createSection("junkStand"), session.getJunkStand());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config for " + uuid, e);
        }
    }

    public void loadSession(UUID uuid, FishySession session) {
        File file = new File(userDataFolder, uuid.toString() + ".yml");
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Load overrides
        ConfigurationSection overridesSection = config.getConfigurationSection("overrides");
        if (overridesSection != null) {
            for (String key : overridesSection.getKeys(false)) {
                try {
                    Material material = Material.valueOf(key);
                    DepositBucket bucket = DepositBucket.valueOf(overridesSection.getString(key));
                    session.getTreasureJunkOverrides().put(material, bucket);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        // Load chest routes
        session.setTreasureChest(loadChestRoute(config.getConfigurationSection("treasureChest")));
        session.setFishChest(loadChestRoute(config.getConfigurationSection("fishChest")));
        session.setJunkStand(loadLocation(config.getConfigurationSection("junkStand")));
    }

    private void saveChestRoute(ConfigurationSection section, ChestRoute route) {
        section.set("world", route.worldId().toString());
        section.set("x", route.x());
        section.set("y", route.y());
        section.set("z", route.z());
        saveLocation(section.createSection("stand"), route.stand());
    }

    private ChestRoute loadChestRoute(ConfigurationSection section) {
        if (section == null) return null;
        String worldStr = section.getString("world");
        if (worldStr == null) return null;
        UUID worldId = UUID.fromString(worldStr);
        int x = section.getInt("x");
        int y = section.getInt("y");
        int z = section.getInt("z");
        Location stand = loadLocation(section.getConfigurationSection("stand"));
        if (stand == null) return null;
        return new ChestRoute(worldId, x, y, z, stand);
    }

    private void saveLocation(ConfigurationSection section, Location loc) {
        section.set("world", loc.getWorld().getUID().toString());
        section.set("x", loc.getX());
        section.set("y", loc.getY());
        section.set("z", loc.getZ());
        section.set("yaw", loc.getYaw());
        section.set("pitch", loc.getPitch());
    }

    private Location loadLocation(ConfigurationSection section) {
        if (section == null) return null;
        String worldStr = section.getString("world");
        if (worldStr == null) return null;
        UUID worldId = UUID.fromString(worldStr);
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw");
        float pitch = (float) section.getDouble("pitch");
        return new Location(Bukkit.getWorld(worldId), x, y, z, yaw, pitch);
    }
}
