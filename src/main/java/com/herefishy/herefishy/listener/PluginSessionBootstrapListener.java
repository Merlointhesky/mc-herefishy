package com.herefishy.herefishy.listener;

import com.herefishy.herefishy.session.FishySessionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PluginSessionBootstrapListener implements Listener {

    private final FishySessionManager sessionManager;

    public PluginSessionBootstrapListener(FishySessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessionManager.clear(event.getPlayer().getUniqueId());
    }
}
