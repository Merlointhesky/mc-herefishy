package com.herefishy.herefishy.session;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FishySessionManager {

    private final Map<UUID, FishySession> sessions = new ConcurrentHashMap<>();

    public FishySession session(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), u -> new FishySession());
    }

    public void clear(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public void clear(UUID uuid) {
        sessions.remove(uuid);
    }
}
