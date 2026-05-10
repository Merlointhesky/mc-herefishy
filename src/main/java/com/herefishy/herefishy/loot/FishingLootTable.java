package com.herefishy.herefishy.loot;

import com.herefishy.herefishy.session.DepositBucket;
import org.bukkit.Material;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Materials used by the simulated loot rolls and GUI defaults. */
public final class FishingLootTable {

    public static final List<Material> FISH_LOOT = List.of(
            Material.COD, Material.COD, Material.COD, Material.COD,
            Material.SALMON, Material.SALMON,
            Material.TROPICAL_FISH,
            Material.PUFFERFISH
    );

    public static final List<Material> JUNK_LOOT = List.of(
            Material.BOWL, Material.LEATHER, Material.LEATHER_BOOTS,
            Material.ROTTEN_FLESH, Material.STICK, Material.STRING,
            Material.POTION, Material.BONE, Material.TRIPWIRE_HOOK,
            Material.INK_SAC
    );

    public static final List<Material> TREASURE_LOOT = List.of(
            Material.ENCHANTED_BOOK,
            Material.BOW,
            Material.FISHING_ROD,
            Material.LILY_PAD,
            Material.NAME_TAG,
            Material.SADDLE,
            Material.NAUTILUS_SHELL
    );

    // Standard treasure (1000 XP)
    public static final List<Material> STANDARD_TREASURE_LOOT = List.of(
            Material.ENCHANTED_BOOK,
            Material.BOW,
            Material.FISHING_ROD,
            Material.LILY_PAD
    );

    // Rare loot (2000 XP)
    public static final List<Material> RARE_TREASURE_LOOT = List.of(
            Material.NAME_TAG,
            Material.SADDLE
    );

    // Epic loot (5000 XP)
    public static final List<Material> EPIC_TREASURE_LOOT = List.of(
            Material.NAUTILUS_SHELL
    );

    private static final EnumSet<Material> FISH_MATERIALS = EnumSet.copyOf(FISH_LOOT);

    public static boolean isFishMaterial(Material material) {
        return FISH_MATERIALS.contains(material);
    }

    /** Stable unique ordering for GUI (junk first, then treasure extras). */
    public static List<Material> configurableMaterialsOrdered() {
        LinkedHashSet<Material> merged = new LinkedHashSet<>(JUNK_LOOT);
        merged.addAll(TREASURE_LOOT);
        return List.copyOf(merged);
    }

    public static Set<Material> defaultTreasureSet() {
        return EnumSet.copyOf(TREASURE_LOOT);
    }

    public static Set<Material> defaultJunkSet() {
        return EnumSet.copyOf(JUNK_LOOT);
    }

    private FishingLootTable() {
    }

    /** Default deposit bucket ignoring per-player overrides. */
    public static DepositBucket defaultDepositOf(Material material) {
        if (isFishMaterial(material)) {
            return DepositBucket.FISH;
        }
        if (defaultTreasureSet().contains(material)) {
            return DepositBucket.TREASURE;
        }
        if (defaultJunkSet().contains(material)) {
            return DepositBucket.JUNK;
        }
        return DepositBucket.JUNK;
    }
}
