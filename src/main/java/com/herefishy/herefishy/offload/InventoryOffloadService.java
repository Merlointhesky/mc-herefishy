package com.herefishy.herefishy.offload;

import com.herefishy.herefishy.HereFishyPlugin;
import com.herefishy.herefishy.listener.FishingListener;
import com.herefishy.herefishy.loot.LootClassifier;
import com.herefishy.herefishy.session.ChestRoute;
import com.herefishy.herefishy.session.DepositBucket;
import com.herefishy.herefishy.session.FishySession;
import com.herefishy.herefishy.session.FishySessionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/** Teleport player through fish chest → treasure chest → junk drops → fishing anchor. */
public final class InventoryOffloadService {

    private static final int OFF_HAND_SLOT = 40;

    private final HereFishyPlugin plugin;
    private final FishySessionManager sessionManager;

    public InventoryOffloadService(HereFishyPlugin plugin, FishySessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
    }

    /**
     * @param rescheduleAfterSuccess when true, schedules the next cast after a successful dump
     * @return true when offload succeeded and auto-fishing may continue
     */
    public boolean run(Player player, Location fishingAnchor, FishingListener listener,
                       boolean rescheduleAfterSuccess) {
        FishySession session = sessionManager.session(player);
        if (!session.routesComplete()) {
            return false;
        }
        if (session.isOffloadInProgress()) {
            return false;
        }
        session.setOffloadInProgress(true);
        session.incrementDumpCount();
        player.sendMessage(Component.text("Inventory full — teleporting to stash loot.")
                .color(NamedTextColor.AQUA));
        try {
            boolean ok = runInternal(player, session, fishingAnchor.clone());
            if (!ok) {
                player.teleport(fishingAnchor);
                listener.stopAutoFishing(player, Component.text(
                        "Auto-fishing stopped — could not offload (chest full or inventory still clogged).")
                        .color(NamedTextColor.RED));
                return false;
            }
            player.sendMessage(Component.text("Loot routed — snapping back to the water.")
                    .color(NamedTextColor.GREEN));
            listener.sendActivitySummary(player, session);
            if (rescheduleAfterSuccess) {
                listener.scheduleReCast(player);
            }
            return true;
        } finally {
            session.setOffloadInProgress(false);
        }
    }

    private boolean runInternal(Player player, FishySession session, Location fishingAnchor) {
        ChestRoute fishRoute = session.getFishChest();
        ChestRoute treasureRoute = session.getTreasureChest();
        Location junkStand = session.getJunkStand();

        ArrayList<ItemStack> buffer = new ArrayList<>(session.drainOverflowStacks());

        Inventory fishChest = fishRoute == null ? null : fishRoute.resolveInventory();
        Inventory treasureChest = treasureRoute == null ? null : treasureRoute.resolveInventory();
        World junkWorld = junkStand == null ? null : junkStand.getWorld();

        if (fishRoute == null || treasureRoute == null || junkStand == null
                || junkWorld == null || fishChest == null || treasureChest == null) {
            session.enqueueOverflowStacks(buffer);
            plugin.getLogger().warning("HereFishy offload aborted (missing routing target) for " + player.getName());
            return false;
        }

        player.teleport(fishRoute.stand().clone());
        if (!depositToChest(session, player, buffer, fishChest, DepositBucket.FISH)
                || stillHas(session, player, buffer, DepositBucket.FISH)) {
            session.enqueueOverflowStacks(buffer);
            return false;
        }

        player.teleport(treasureRoute.stand().clone());
        if (!depositToChest(session, player, buffer, treasureChest, DepositBucket.TREASURE)
                || stillHas(session, player, buffer, DepositBucket.TREASURE)) {
            session.enqueueOverflowStacks(buffer);
            return false;
        }

        player.teleport(junkStand.clone());
        if (!purgeJunk(session, player, buffer, junkWorld, junkStand.clone())
                || stillHas(session, player, buffer, DepositBucket.JUNK)) {
            session.enqueueOverflowStacks(buffer);
            return false;
        }

        if (!buffer.isEmpty()) {
            session.enqueueOverflowStacks(buffer);
            plugin.getLogger().warning("HereFishy offload buffer still had leftovers for " + player.getName());
            return false;
        }

        player.teleport(fishingAnchor);
        return player.getInventory().firstEmpty() != -1;
    }

    private boolean depositToChest(FishySession session, Player player,
                                   List<ItemStack> buffer,
                                   Inventory chest,
                                   DepositBucket bucket) {
        if (!drainBufferIntoChest(buffer, chest, session, bucket)) {
            return false;
        }
        return drainInventoryIntoChest(player, chest, session, bucket);
    }

    private boolean drainBufferIntoChest(List<ItemStack> buffer,
                                         Inventory chest,
                                         FishySession session,
                                         DepositBucket bucket) {
        Iterator<ItemStack> iterator = buffer.iterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            if (stack == null || stack.getType().isAir()) {
                iterator.remove();
                continue;
            }
            if (LootClassifier.depositBucket(stack.getType(), session) != bucket) {
                continue;
            }
            if (!depositWholeStack(stack, chest)) {
                return false;
            }
            iterator.remove();
        }
        return true;
    }

    private boolean drainInventoryIntoChest(Player player,
                                            Inventory chest,
                                            FishySession session,
                                            DepositBucket bucket) {
        PlayerInventory inventory = player.getInventory();
        for (int slot : movableSlotsIterable(inventory)) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (LootClassifier.depositBucket(stack.getType(), session) != bucket) {
                continue;
            }
            if (!depositWholeStack(stack, chest)) {
                return false;
            }
            inventory.setItem(slot, null);
        }
        return true;
    }

    private boolean purgeJunk(FishySession session,
                              Player player,
                              List<ItemStack> buffer,
                              World world,
                              Location dropSpot) {
        Iterator<ItemStack> iterator = buffer.iterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            if (stack == null || stack.getType().isAir()) {
                iterator.remove();
                continue;
            }
            if (LootClassifier.depositBucket(stack.getType(), session) != DepositBucket.JUNK) {
                continue;
            }
            world.dropItem(dropSpot.clone().add(0, 0.1, 0), stack.clone());
            iterator.remove();
        }

        PlayerInventory inventory = player.getInventory();
        for (int slot : movableSlotsIterable(inventory)) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (LootClassifier.depositBucket(stack.getType(), session) != DepositBucket.JUNK) {
                continue;
            }
            inventory.setItem(slot, null);
            world.dropItem(dropSpot.clone().add(0, 0.1, 0), stack.clone());
        }
        return true;
    }

    private boolean stillHas(FishySession session,
                             Player player,
                             List<ItemStack> buffer,
                             DepositBucket bucket) {
        for (ItemStack stack : buffer) {
            if (stack != null && !stack.getType().isAir()
                    && LootClassifier.depositBucket(stack.getType(), session) == bucket) {
                return true;
            }
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot : movableSlotsIterable(inventory)) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (LootClassifier.depositBucket(stack.getType(), session) == bucket) {
                return true;
            }
        }
        return false;
    }

    private boolean depositWholeStack(ItemStack stack, Inventory chest) {
        HashMap<Integer, ItemStack> leftover = chest.addItem(stack.clone());
        return leftover.isEmpty();
    }

    private Iterable<Integer> movableSlotsIterable(PlayerInventory inventory) {
        ArrayList<Integer> slots = new ArrayList<>(37);
        int rodSafeSlot = inventory.getHeldItemSlot();
        for (int i = 0; i < 36; i++) {
            if (i == rodSafeSlot && inventory.getItemInMainHand().getType() == org.bukkit.Material.FISHING_ROD) {
                continue;
            }
            slots.add(i);
        }
        if (inventory.getItemInOffHand().getType() != org.bukkit.Material.FISHING_ROD) {
            slots.add(OFF_HAND_SLOT);
        }
        return slots;
    }
}
