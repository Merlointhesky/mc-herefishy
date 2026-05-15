package com.herefishy.herefishy.listener;

import com.herefishy.herefishy.config.PlayerConfigManager;
import com.herefishy.herefishy.gui.RoutingConfigMenu;
import com.herefishy.herefishy.loot.FishingLootTable;
import com.herefishy.herefishy.session.FishySession;
import com.herefishy.herefishy.session.FishySessionManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.List;

public final class RoutingMenuListener implements Listener {

    private final FishySessionManager sessionManager;
    private final PlayerConfigManager configManager;

    public RoutingMenuListener(FishySessionManager sessionManager, PlayerConfigManager configManager) {
        this.sessionManager = sessionManager;
        this.configManager = configManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!event.getView().title().equals(RoutingConfigMenu.TITLE)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        List<Material> materials = FishingLootTable.configurableMaterialsOrdered();
        int limit = Math.min(materials.size(), event.getView().getTopInventory().getSize());
        if (slot < 0 || slot >= limit) {
            return;
        }
        Material material = materials.get(slot);
        FishySession session = sessionManager.session(player);
        session.cycleTreasureJunk(material);
        configManager.saveSession(player.getUniqueId(), session);
        event.getView().getTopInventory().setItem(slot, RoutingConfigMenu.iconFor(session, material));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!event.getView().title().equals(RoutingConfigMenu.TITLE)) {
            return;
        }
        event.setCancelled(true);
    }
}
