package com.herefishy.herefishy.session;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** A block-stored container plus the safe stand where the player was when they bound it. */
public final class ChestRoute {

    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;
    private final Location stand;

    public ChestRoute(UUID worldId, int x, int y, int z, Location stand) {
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.stand = stand.clone();
    }

    public Location stand() {
        return stand.clone();
    }

    public UUID worldId() {
        return worldId;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public @Nullable Inventory resolveInventory() {
        World world = Bukkit.getWorld(worldId);
        if (world == null) {
            return null;
        }
        Block block = world.getBlockAt(x, y, z);
        BlockState state = block.getState();
        if (!(state instanceof InventoryHolder holder)) {
            return null;
        }
        return holder.getInventory();
    }

    public static @Nullable Inventory inventoryAt(Block block) {
        BlockState state = block.getState();
        if (!(state instanceof InventoryHolder holder)) {
            return null;
        }
        return holder.getInventory();
    }

    public boolean sameContainer(Block block) {
        Inventory a = resolveInventory();
        Inventory b = inventoryAt(block);
        return a != null && b != null && a == b;
    }

    public boolean sameContainer(ChestRoute other) {
        if (other == null) {
            return false;
        }
        Inventory a = resolveInventory();
        Inventory b = other.resolveInventory();
        return a != null && b != null && a == b;
    }
}
