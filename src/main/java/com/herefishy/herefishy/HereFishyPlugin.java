package com.herefishy.herefishy;

import com.herefishy.herefishy.command.HereFishyCommand;
import com.herefishy.herefishy.listener.FishingListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class HereFishyPlugin extends JavaPlugin {

    private static HereFishyPlugin instance;
    private final FishingListener fishingListener = new FishingListener();

    @Override
    public void onEnable() {
        instance = this;
        getCommand("herefishy").setExecutor(new HereFishyCommand(fishingListener));
        getServer().getPluginManager().registerEvents(fishingListener, this);
        getLogger().info("HereFishy enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("HereFishy disabled!");
    }

    public static HereFishyPlugin getInstance() {
        return instance;
    }
}
