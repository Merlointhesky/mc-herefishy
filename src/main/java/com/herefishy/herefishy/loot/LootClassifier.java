package com.herefishy.herefishy.loot;

import com.herefishy.herefishy.session.DepositBucket;
import com.herefishy.herefishy.session.FishySession;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.EnumMap;

/**
 * Classification for chat color, AuraSkills XP, and offload routing.
 * Fish buckets are immutable; configurable materials use session overrides treasure↔junk.
 */
public final class LootClassifier {

    private static final EnumMap<Material, Double> MATERIAL_XP_VALUES = new EnumMap<>(Material.class);

    static {
        // Fish XP values (base, before level multiplier)
        MATERIAL_XP_VALUES.put(Material.COD, 25.0);
        MATERIAL_XP_VALUES.put(Material.SALMON, 60.0);
        MATERIAL_XP_VALUES.put(Material.TROPICAL_FISH, 750.0);
        MATERIAL_XP_VALUES.put(Material.PUFFERFISH, 115.0);

        // Junk XP values (default category, not affected by player routing config)
        MATERIAL_XP_VALUES.put(Material.BOWL, 30.0);
        MATERIAL_XP_VALUES.put(Material.LEATHER, 30.0);
        MATERIAL_XP_VALUES.put(Material.LEATHER_BOOTS, 30.0);
        MATERIAL_XP_VALUES.put(Material.ROTTEN_FLESH, 30.0);
        MATERIAL_XP_VALUES.put(Material.STICK, 30.0);
        MATERIAL_XP_VALUES.put(Material.STRING, 30.0);
        MATERIAL_XP_VALUES.put(Material.POTION, 30.0);
        MATERIAL_XP_VALUES.put(Material.BONE, 30.0);
        MATERIAL_XP_VALUES.put(Material.TRIPWIRE_HOOK, 30.0);
        MATERIAL_XP_VALUES.put(Material.INK_SAC, 30.0);

        // Treasure XP values (default category, not affected by player routing config)
        MATERIAL_XP_VALUES.put(Material.ENCHANTED_BOOK, 1000.0);
        MATERIAL_XP_VALUES.put(Material.BOW, 1000.0);
        MATERIAL_XP_VALUES.put(Material.FISHING_ROD, 1000.0);
        MATERIAL_XP_VALUES.put(Material.LILY_PAD, 1000.0);
        MATERIAL_XP_VALUES.put(Material.NAME_TAG, 2000.0);
        MATERIAL_XP_VALUES.put(Material.SADDLE, 2000.0);
        MATERIAL_XP_VALUES.put(Material.NAUTILUS_SHELL, 5000.0);
    }

    private LootClassifier() {
    }

    public static DepositBucket depositBucket(Material material, FishySession session) {
        if (FishingLootTable.isFishMaterial(material)) {
            return DepositBucket.FISH;
        }
        EnumMap<Material, DepositBucket> overrides = session.getTreasureJunkOverrides();
        DepositBucket o = overrides.get(material);
        if (o != null && (o == DepositBucket.TREASURE || o == DepositBucket.JUNK)) {
            return o;
        }
        return FishingLootTable.defaultDepositOf(material);
    }

    /** Lily pads stay visually distinct when still routed as treasure. */
    public static NamedTextColor chatColor(Material material, FishySession session) {
        DepositBucket bucket = depositBucket(material, session);
        return switch (bucket) {
            case TREASURE -> material == Material.LILY_PAD ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
            case JUNK -> NamedTextColor.RED;
            case FISH -> NamedTextColor.YELLOW;
        };
    }

    public static double auraXpBase(Material material, FishySession session) {
        // XP is based on the DEFAULT loot category, not player's custom routing config
        // This ensures XP rewards are consistent regardless of treasure/junk reclassification
        return MATERIAL_XP_VALUES.getOrDefault(material, 30.0);
    }
}
