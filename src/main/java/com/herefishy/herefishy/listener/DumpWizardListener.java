package com.herefishy.herefishy.listener;

import com.herefishy.herefishy.session.ChestRoute;
import com.herefishy.herefishy.session.FishySession;
import com.herefishy.herefishy.session.FishySessionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;

/**
 * Sneak + fishing rod binds treasure chest → fish chest → junk teleport spot (session-only storage).
 */
public final class DumpWizardListener implements Listener {

    private final FishySessionManager sessionManager;

    public DumpWizardListener(FishySessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.hasBlock()) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        if (!isRodEquipped(player)) {
            return;
        }

        FishySession session = sessionManager.session(player);
        Block block = event.getClickedBlock();

        if (session.routesComplete()) {
            session.resetDumpBindings();
            player.sendMessage(Component.text("Previous dump route discarded — configuring a fresh one.")
                    .color(NamedTextColor.YELLOW));
        }

        ChestRoute treasure = session.getTreasureChest();
        ChestRoute fish = session.getFishChest();
        if (treasure == null) {
            bindTreasureChest(player, session, block, event);
            return;
        }
        if (fish == null) {
            bindFishChest(player, session, block, treasure, event);
            return;
        }
        if (session.getJunkStand() == null) {
            bindJunkSpot(player, session, block, event);
        }
    }

    private void bindTreasureChest(Player player, FishySession session, Block block, PlayerInteractEvent event) {
        if (!(block.getState() instanceof InventoryHolder)) {
            player.sendMessage(Component.text("Treasure stash must be an inventory block (chest, barrel, …).")
                    .color(NamedTextColor.RED));
            return;
        }
        ChestRoute route = chestRoute(block, player);
        session.resetDumpBindings(); // wipes partial binds but keeps classifier overrides intact
        session.setTreasureChest(route);
        event.setCancelled(true);
        player.sendMessage(Component.text("Treasure routing locked.")
                .color(NamedTextColor.GREEN));
        player.sendMessage(Component.text("Sneak-click the fish barrel/chest next.")
                .color(NamedTextColor.YELLOW));
    }

    private void bindFishChest(Player player,
                               FishySession session,
                               Block block,
                               ChestRoute treasure,
                               PlayerInteractEvent event) {
        if (!(block.getState() instanceof InventoryHolder)) {
            player.sendMessage(Component.text("Fish crates need an inventory block — try again.")
                    .color(NamedTextColor.RED));
            return;
        }
        ChestRoute candidate = chestRoute(block, player);
        if (treasure != null && treasure.sameContainer(block)) {
            player.sendMessage(Component.text("Choose a chest that is NOT your trophy stash.")
                    .color(NamedTextColor.RED));
            return;
        }
        session.setFishChest(candidate);
        event.setCancelled(true);
        player.sendMessage(Component.text("Fish stash locked.")
                .color(NamedTextColor.GREEN));
        player.sendMessage(Component.text("Now sneak-click the block next to lava (junk chute anchor).")
                .color(NamedTextColor.YELLOW));
    }

    private void bindJunkSpot(Player player, FishySession session, Block block, PlayerInteractEvent event) {
        session.setJunkStand(player.getLocation().clone());
        player.sendMessage(Component.text("Everything's wired — junk will tumble from where you stood.")
                .color(NamedTextColor.GREEN));
    }

    private ChestRoute chestRoute(Block block, Player player) {
        return new ChestRoute(
                player.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ(),
                player.getLocation().clone());
    }

    private static boolean isRodEquipped(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.FISHING_ROD
                || player.getInventory().getItemInOffHand().getType() == Material.FISHING_ROD;
    }
}
