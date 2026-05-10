package com.herefishy.herefishy.session;

import com.herefishy.herefishy.loot.FishingLootTable;
import com.herefishy.herefishy.loot.LootClassifier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class FishySession {

    private boolean autoFishing;
    private volatile boolean offloadInProgress;
    private ChestRoute treasureChest;
    private ChestRoute fishChest;
    /** Teleport destination for dumping junk items (drops at feet). */
    private Location junkStand;
    /** Only TREASURE / JUNK values are stored as explicit overrides for configurable materials. */
    private final EnumMap<Material, DepositBucket> treasureJunkOverrides = new EnumMap<>(Material.class);

    /** Catches `addItem` overflow when autofishing with a stuffed inventory — drained by offload logic. */
    private final Deque<ItemStack> overflowStacks = new ArrayDeque<>();

    public boolean isAutoFishing() {
        return autoFishing;
    }

    public void setAutoFishing(boolean autoFishing) {
        this.autoFishing = autoFishing;
    }

    public boolean isOffloadInProgress() {
        return offloadInProgress;
    }

    public void setOffloadInProgress(boolean offloadInProgress) {
        this.offloadInProgress = offloadInProgress;
    }

    public ChestRoute getTreasureChest() {
        return treasureChest;
    }

    public void setTreasureChest(ChestRoute treasureChest) {
        this.treasureChest = treasureChest;
    }

    public ChestRoute getFishChest() {
        return fishChest;
    }

    public void setFishChest(ChestRoute fishChest) {
        this.fishChest = fishChest;
    }

    public Location getJunkStand() {
        return junkStand == null ? null : junkStand.clone();
    }

    public void setJunkStand(Location junkStand) {
        this.junkStand = junkStand == null ? null : junkStand.clone();
    }

    public Map<Material, DepositBucket> getTreasureJunkOverridesView() {
        return Map.copyOf(treasureJunkOverrides);
    }

    /** Mutable map exposed for classifier / GUI listeners in-plugin only. */
    public EnumMap<Material, DepositBucket> getTreasureJunkOverrides() {
        return treasureJunkOverrides;
    }

    public boolean routesComplete() {
        return treasureChest != null && fishChest != null && junkStand != null;
    }

    /** Clears teleport/chest bindings only — keeps classifier GUI overrides intact. */
    public void resetDumpBindings() {
        treasureChest = null;
        fishChest = null;
        junkStand = null;
        overflowStacks.clear();
    }

    /** Full routing + overflow reset (disconnect / explicit wipe use). */
    public void resetRoutes() {
        resetDumpBindings();
        treasureJunkOverrides.clear();
    }

    public void clearAll() {
        autoFishing = false;
        offloadInProgress = false;
        resetRoutes();
    }

    public void enqueueOverflowStacks(Collection<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            overflowStacks.addLast(stack.clone());
        }
    }

    public boolean hasBufferedOverflow() {
        return !overflowStacks.isEmpty();
    }

    public List<ItemStack> drainOverflowStacks() {
        ArrayList<ItemStack> copy = new ArrayList<>(overflowStacks);
        overflowStacks.clear();
        return copy;
    }

    /** Next toggle for configurable materials between treasure chest and junk spot. */
    public void cycleTreasureJunk(Material material) {
        if (FishingLootTable.isFishMaterial(material)) {
            return;
        }
        if (!FishingLootTable.configurableMaterialsOrdered().contains(material)) {
            return;
        }
        DepositBucket current = LootClassifier.depositBucket(material, this);
        DepositBucket flipped = current == DepositBucket.JUNK ? DepositBucket.TREASURE : DepositBucket.JUNK;
        if (flipped == FishingLootTable.defaultDepositOf(material)) {
            treasureJunkOverrides.remove(material);
        } else {
            treasureJunkOverrides.put(material, flipped);
        }
    }
}
