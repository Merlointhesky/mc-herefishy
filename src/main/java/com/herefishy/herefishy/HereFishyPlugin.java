package com.herefishy.herefishy;

import com.herefishy.herefishy.command.HereFishyCommand;
import com.herefishy.herefishy.command.HereFishyTabCompleter;
import com.herefishy.herefishy.listener.DumpWizardListener;
import com.herefishy.herefishy.listener.FishingListener;
import com.herefishy.herefishy.listener.PluginSessionBootstrapListener;
import com.herefishy.herefishy.listener.RoutingMenuListener;
import com.herefishy.herefishy.offload.InventoryOffloadService;
import com.herefishy.herefishy.session.FishySessionManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class HereFishyPlugin extends JavaPlugin {

    private static HereFishyPlugin instance;
    private final FishySessionManager sessionManager = new FishySessionManager();
    private final InventoryOffloadService offloadService = new InventoryOffloadService(this, sessionManager);
    private final FishingListener fishingListener = new FishingListener(sessionManager, offloadService);

    @Override
    public void onEnable() {
        instance = this;
        var command = getCommand("herefishy");
        if (command != null) {
            HereFishyCommand executor = new HereFishyCommand(fishingListener, sessionManager);
            command.setExecutor(executor);
            command.setTabCompleter(new HereFishyTabCompleter());
        }
        registerListeners();
        getLogger().info("HereFishy enabled!");
    }

    private void registerListeners() {
        var plugins = getServer().getPluginManager();
        plugins.registerEvents(fishingListener, this);
        plugins.registerEvents(new DumpWizardListener(sessionManager), this);
        plugins.registerEvents(new RoutingMenuListener(sessionManager), this);
        plugins.registerEvents(new PluginSessionBootstrapListener(sessionManager), this);
    }

    @Override
    public void onDisable() {
        getLogger().info("HereFishy disabled!");
    }

    public static HereFishyPlugin getInstance() {
        return instance;
    }

    public FishySessionManager getSessionManager() {
        return sessionManager;
    }
}
