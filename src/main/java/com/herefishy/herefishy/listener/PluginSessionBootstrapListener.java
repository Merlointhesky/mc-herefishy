package com.herefishy.herefishy.listener;

import com.herefishy.herefishy.config.PlayerConfigManager;
import com.herefishy.herefishy.session.FishySessionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PluginSessionBootstrapListener implements Listener {

    private final FishySessionManager sessionManager;
    private final PlayerConfigManager configManager;

    public PluginSessionBootstrapListener(FishySessionManager sessionManager, PlayerConfigManager configManager) {
        this.sessionManager = sessionManager;
        this.configManager = configManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var session = sessionManager.session(player);
        configManager.loadSession(player.getUniqueId(), session);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        var session = sessionManager.session(player);
        configManager.saveSession(player.getUniqueId(), session);
        sessionManager.clear(player.getUniqueId());
    }
}
