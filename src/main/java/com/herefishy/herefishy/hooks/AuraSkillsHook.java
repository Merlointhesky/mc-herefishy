package com.herefishy.herefishy.hooks;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import com.herefishy.herefishy.session.FishySession;
import com.herefishy.herefishy.loot.LootClassifier;

public final class AuraSkillsHook {

    public static int getFishingLevel(Player player) {
        try {
            AuraSkillsApi auraSkills = AuraSkillsApi.get();
            SkillsUser user = auraSkills.getUser(player.getUniqueId());
            if (user != null) {
                return user.getSkillLevel(Skills.FISHING);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    public static void awardFishingXp(Player player, ItemStack loot, FishySession session) {
        try {
            AuraSkillsApi auraSkills = AuraSkillsApi.get();
            SkillsUser user = auraSkills.getUser(player.getUniqueId());
            if (user != null) {
                double baseXp = LootClassifier.auraXpBase(loot.getType(), session);
                int auraFishingLevel = getFishingLevel(player);
                double xpAmount = baseXp * (1.0 + auraFishingLevel * 0.02);
                user.addSkillXp(Skills.FISHING, xpAmount);
            }
        } catch (Throwable ignored) {
        }
    }
}
