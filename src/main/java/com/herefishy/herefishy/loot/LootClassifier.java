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
        DepositBucket bucket = depositBucket(material, session);
        return switch (bucket) {
            case TREASURE -> material == Material.LILY_PAD ? 35.0 : 50.0;
            case JUNK -> 15.0;
            case FISH -> 25.0;
        };
    }
}
