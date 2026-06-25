package com.herefishy.herefishy.listener;

import com.herefishy.herefishy.HereFishyPlugin;
import com.herefishy.herefishy.loot.FishingLootTable;
import com.herefishy.herefishy.loot.LootClassifier;
import com.herefishy.herefishy.offload.InventoryOffloadService;
import com.herefishy.herefishy.session.FishySession;
import com.herefishy.herefishy.session.FishySessionManager;
import com.herefishy.herefishy.task.AutoDefenseTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Spider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class FishingListener implements Listener {

    private static final int LOW_DURABILITY_THRESHOLD = 5;

    private final FishySessionManager sessionManager;
    private final InventoryOffloadService offloadService;
    private final Map<UUID, AutoDefenseTask> activeDefenseTasks = new HashMap<>();

    private static final Random RANDOM = new Random();

    public FishingListener(FishySessionManager sessionManager, InventoryOffloadService offloadService) {
        this.sessionManager = sessionManager;
        this.offloadService = offloadService;
    }

    public boolean isAutoFishing(Player player) {
        return sessionManager.session(player).isAutoFishing();
    }

    public void startAutoFishing(Player player) {
        FishySession session = sessionManager.session(player);
        session.clearStats();
        session.setAutoFishing(true);
        stopAutoDefense(player, true);
        startAutoDefense(player);
    }

    public void stopAutoFishing(Player player) {
        FishySession session = sessionManager.session(player);
        if (session.isAutoFishing()) {
            session.setAutoFishing(false);
            sendActivitySummary(player, session);
            stopAutoDefense(player, true);
            if (player.isOnline() && HereFishyPlugin.getInstance().isEnabled()) {
                startAutoDefense(player);
            }
        }
    }

    public void stopAutoFishing(Player player, @Nullable Component message) {
        stopAutoFishing(player);
        if (message != null) {
            player.sendMessage(message);
        }
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (!isAutoFishing(player)) {
            return;
        }

        switch (event.getState()) {
            case BITE -> {
                boolean suppressRecast = deliverCatch(player);
                int auraFishingLevel = getAuraSkillsFishingLevel(player);
                int baseXp = 1 + RANDOM.nextInt(6);
                int xpAwarded = (int) Math.round(baseXp * (1.0 + auraFishingLevel * 0.02));
                player.giveExp(xpAwarded);

                ItemStack rod = getHeldFishingRod(player);
                if (rod != null) {
                    if (!damageFishingRod(player, rod, xpAwarded)) {
                        stopAutoFishing(player,
                                Component.text("Auto-fishing stopped — your fishing rod broke!")
                                        .color(NamedTextColor.RED));
                        event.getHook().remove();
                        return;
                    }
                }

                FishHook hook = event.getHook();
                if (hook != null && !hook.isDead()) {
                    hook.remove();
                }

                if (!suppressRecast) {
                    scheduleReCast(player);
                }
            }

            case CAUGHT_FISH -> {
                ItemStack rod = getHeldFishingRod(player);
                if (rod != null) {
                    if (!damageFishingRod(player, rod, 0)) {
                        stopAutoFishing(player,
                                Component.text("Auto-fishing stopped — your fishing rod broke!")
                                        .color(NamedTextColor.RED));
                        return;
                    }
                }
                scheduleReCast(player);
            }

            case FAILED_ATTEMPT, IN_GROUND -> scheduleReCast(player);

            default -> {
            }
        }
    }

    /**
     * @return true when downstream logic should skip scheduling another immediate cast because offload handled it or autofishing stopped.
     */
    private boolean deliverCatch(Player player) {
        FishySession session = sessionManager.session(player);
        ItemStack loot = generateFishingLoot(player);
        if (loot == null || loot.getType() == Material.AIR) {
            return false;
        }

        Location fishingAnchor = player.getLocation().clone();
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(loot);
        
        // Record the catch for the summary instead of spamming chat
        session.recordCatch(loot.getType(), LootClassifier.depositBucket(loot.getType(), session), loot.getAmount());
        
        awardAuraSkillsFishingXp(player, loot);

        if (leftover.isEmpty()) {
            return false;
        }

        Collection<ItemStack> spill = leftover.values();

        if (session.routesComplete()) {
            session.enqueueOverflowStacks(spill);
            offloadService.run(player, fishingAnchor, this, true);
            return true;
        }

        for (ItemStack overflow : spill) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow.clone());
        }
        return false;
    }

    public void scheduleReCast(Player player) {
        int auraFishingLevel = getAuraSkillsFishingLevel(player);
        long delay = Math.max(5L, 15L - (auraFishingLevel / 10L));
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                FishySession session = sessionManager.session(player);
                if (!session.isAutoFishing()) {
                    return;
                }
                if (session.isOffloadInProgress()) {
                    scheduleReCast(player);
                    return;
                }
                if (!isHoldingFishingRod(player)) {
                    if (hasActiveDefenseTarget(player)) {
                        return; // Exit recast: defense task will trigger recast when threats are clear
                    }
                    stopAutoFishing(player);
                    return;
                }

                if (isRodDurabilityLow(player)) {
                    stopAutoFishing(player, Component.text("Auto-fishing stopped — fishing rod is nearly broken!")
                            .color(NamedTextColor.RED));
                    return;
                }

                Location fishingAnchor = player.getLocation().clone();
                if (isInventoryFull(player)) {
                    FishySession active = sessionManager.session(player);
                    if (active.routesComplete()) {
                        offloadService.run(player, fishingAnchor, FishingListener.this, true);
                    } else {
                        stopAutoFishing(player, Component.text("Auto-fishing stopped — inventory full!")
                                .color(NamedTextColor.RED));
                    }
                    return;
                }

                Vector direction = player.getLocation().getDirection();
                FishHook newHook = player.launchProjectile(FishHook.class, direction.multiply(0.8));
                if (newHook != null) {
                    newHook.setVelocity(direction.multiply(0.8));
                }
            }
        }.runTaskLater(HereFishyPlugin.getInstance(), delay);
    }

    private boolean isInventoryFull(Player player) {
        return player.getInventory().firstEmpty() == -1 || sessionManager.session(player).hasBufferedOverflow();
    }

    public void sendActivitySummary(Player player, FishySession session) {
        if (session.getCaughtFish().isEmpty() && session.getCaughtJunk().isEmpty() && session.getCaughtTreasure().isEmpty()) {
            return;
        }

        player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("#HEREFISHY ACTIVITY SUMMARY:").color(NamedTextColor.GOLD));

        if (!session.getCaughtFish().isEmpty()) {
            player.sendMessage(Component.text("Fish Caught:").color(NamedTextColor.YELLOW));
            for (Map.Entry<Material, Integer> entry : session.getCaughtFish().entrySet()) {
                player.sendMessage(Component.text("  - " + formatItemName(entry.getKey().name()) + ": " + entry.getValue())
                        .color(NamedTextColor.WHITE));
            }
        }

        if (!session.getCaughtTreasure().isEmpty()) {
            player.sendMessage(Component.text("Treasure Found:").color(NamedTextColor.GREEN));
            for (Map.Entry<Material, Integer> entry : session.getCaughtTreasure().entrySet()) {
                player.sendMessage(Component.text("  - " + formatItemName(entry.getKey().name()) + ": " + entry.getValue())
                        .color(NamedTextColor.WHITE));
            }
        }

        if (!session.getCaughtJunk().isEmpty()) {
            player.sendMessage(Component.text("Junk Pulled:").color(NamedTextColor.RED));
            for (Map.Entry<Material, Integer> entry : session.getCaughtJunk().entrySet()) {
                player.sendMessage(Component.text("  - " + formatItemName(entry.getKey().name()) + ": " + entry.getValue())
                        .color(NamedTextColor.WHITE));
            }
        }

        if (session.getDumpCount() > 0) {
            player.sendMessage(Component.text("# Number of Dumps: " + session.getDumpCount()).color(NamedTextColor.AQUA));
        }

        player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));

        session.clearStats();
    }

    private String formatItemName(String materialName) {
        String[] words = materialName.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(" ");
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private void awardAuraSkillsFishingXp(Player player, ItemStack loot) {
        if (Bukkit.getPluginManager().getPlugin("AuraSkills") == null) {
            return;
        }
        try {
            FishySession session = sessionManager.session(player);
            com.herefishy.herefishy.hooks.AuraSkillsHook.awardFishingXp(player, loot, session);
        } catch (Throwable ignored) {
            // AuraSkills integration failed, but auto-fishing continues
        }
    }

    private int getAuraSkillsFishingLevel(Player player) {
        if (Bukkit.getPluginManager().getPlugin("AuraSkills") == null) {
            return 0;
        }
        try {
            return com.herefishy.herefishy.hooks.AuraSkillsHook.getFishingLevel(player);
        } catch (Throwable ignored) {
            // AuraSkills integration failed
        }
        return 0;
    }

    private ItemStack generateFishingLoot(Player player) {
        double roll = RANDOM.nextDouble();
        int luckLevel = getLuckLevel(player);

        int auraFishingLevel = getAuraSkillsFishingLevel(player);
        double treasureChance = 0.05 + (luckLevel * 0.01) + (auraFishingLevel * 0.0025);
        double junkChance = 0.10 - (luckLevel * 0.005) - (auraFishingLevel * 0.0025);
        treasureChance = Math.min(treasureChance, 0.35);
        junkChance = Math.max(junkChance, 0.0);
        double fishChance = 1.0 - treasureChance - junkChance;
        if (fishChance < 0) {
            fishChance = 0;
        }

        if (roll < treasureChance) {
            return generateTreasureLoot(player);
        } else if (roll < treasureChance + junkChance) {
            return generateJunkLoot();
        } else {
            return generateFishLoot();
        }
    }

    private int getLuckLevel(Player player) {
        ItemStack rod = getHeldFishingRod(player);
        if (rod == null || !rod.hasItemMeta()) {
            return 0;
        }
        return rod.getItemMeta().getEnchantLevel(Enchantment.LUCK_OF_THE_SEA);
    }

    private ItemStack generateFishLoot() {
        Material fish = FishingLootTable.FISH_LOOT.get(RANDOM.nextInt(FishingLootTable.FISH_LOOT.size()));
        return new ItemStack(fish, 1);
    }

    private ItemStack generateJunkLoot() {
        Material junk = FishingLootTable.JUNK_LOOT.get(RANDOM.nextInt(FishingLootTable.JUNK_LOOT.size()));
        ItemStack item = new ItemStack(junk, 1);

        if (junk == Material.LEATHER_BOOTS && item.getItemMeta() instanceof Damageable damageable) {
            damageable.setDamage((int) (Material.LEATHER_BOOTS.getMaxDurability() * RANDOM.nextDouble()));
            item.setItemMeta(damageable);
        }

        return item;
    }

    private ItemStack generateTreasureLoot(Player player) {
        // Rarity tiers affected by AuraSkills fishing level bonus
        int auraFishingLevel = getAuraSkillsFishingLevel(player);
        double rarityRoll = RANDOM.nextDouble();
        Material treasure;

        // Base rarity chances: Standard (85%), Rare (14%), Epic (1%)
        // Increased by 0.5% per fishing level for rare and epic, but capped
        double rareChanceBonus = Math.min(0.20, auraFishingLevel * 0.005);
        double epicChanceBonus = Math.min(0.10, auraFishingLevel * 0.005);
        
        double rareChance = 0.14 + rareChanceBonus;
        double epicChance = 0.01 + epicChanceBonus;
        double standardChance = 1.0 - rareChance - epicChance;

        if (rarityRoll < standardChance) {
            // Standard treasure (1000 XP)
            treasure = FishingLootTable.STANDARD_TREASURE_LOOT.get(RANDOM.nextInt(FishingLootTable.STANDARD_TREASURE_LOOT.size()));
        } else if (rarityRoll < standardChance + rareChance) {
            // Rare treasure (2000 XP)
            treasure = FishingLootTable.RARE_TREASURE_LOOT.get(RANDOM.nextInt(FishingLootTable.RARE_TREASURE_LOOT.size()));
        } else {
            // Epic treasure (5000 XP)
            treasure = FishingLootTable.EPIC_TREASURE_LOOT.get(RANDOM.nextInt(FishingLootTable.EPIC_TREASURE_LOOT.size()));
        }

        ItemStack item = new ItemStack(treasure, 1);

        return switch (treasure) {
            case ENCHANTED_BOOK -> {
                ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
                EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
                if (meta != null) {
                    Enchantment[] enchantments = Enchantment.values();
                    Enchantment randomEnchant = enchantments[RANDOM.nextInt(enchantments.length)];
                    int level = 1 + RANDOM.nextInt(randomEnchant.getMaxLevel());
                    meta.addStoredEnchant(randomEnchant, level, true);
                    book.setItemMeta(meta);
                }
                yield book;
            }
            case BOW, FISHING_ROD -> {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    if (RANDOM.nextBoolean()) {
                        meta.addEnchant(Enchantment.UNBREAKING, 1 + RANDOM.nextInt(3), true);
                    }
                    if (item.getType() == Material.FISHING_ROD && RANDOM.nextBoolean()) {
                        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1 + RANDOM.nextInt(3), true);
                    }
                    if (item.getType() == Material.FISHING_ROD && RANDOM.nextBoolean()) {
                        meta.addEnchant(Enchantment.LURE, 1 + RANDOM.nextInt(3), true);
                    }
                    if (item.getType() == Material.BOW && RANDOM.nextBoolean()) {
                        meta.addEnchant(Enchantment.POWER, 1 + RANDOM.nextInt(5), true);
                    }
                    if (RANDOM.nextBoolean()) {
                        meta.addEnchant(Enchantment.MENDING, 1, true);
                    }
                    item.setItemMeta(meta);
                }
                yield item;
            }
            default -> item;
        };
    }

    private boolean isHoldingFishingRod(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        return mainHand.getType() == Material.FISHING_ROD || offHand.getType() == Material.FISHING_ROD;
    }

    private ItemStack getHeldFishingRod(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType() == Material.FISHING_ROD) {
            return mainHand;
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand.getType() == Material.FISHING_ROD) {
            return offHand;
        }
        return null;
    }

    private boolean damageFishingRod(Player player, ItemStack rod, int xpAwarded) {
        ItemMeta meta = rod.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return true;
        }

        Enchantment mending = Enchantment.getByKey(NamespacedKey.minecraft("mending"));
        if (mending != null && meta.hasEnchant(mending) && xpAwarded > 0 && damageable.getDamage() > 0) {
            int durabilityRepaired = xpAwarded / 2;
            int newDamage = Math.max(0, damageable.getDamage() - durabilityRepaired);
            damageable.setDamage(newDamage);
            rod.setItemMeta(meta);
            updateRodInInventory(player, rod);
            return true;
        }

        Enchantment unbreaking = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
        int unbreakingLevel = (unbreaking != null && meta.hasEnchant(unbreaking)) ? meta.getEnchantLevel(unbreaking) : 0;
        if (unbreakingLevel > 0) {
            double chanceToIgnoreDamage = unbreakingLevel / (unbreakingLevel + 1.0);
            if (RANDOM.nextDouble() < chanceToIgnoreDamage) {
                return true;
            }
        }

        int newDamage = damageable.getDamage() + 1;
        int maxDurability = rod.getType().getMaxDurability();

        if (newDamage >= maxDurability) {
            if (player.getInventory().getItemInMainHand().getType() == Material.FISHING_ROD) {
                player.getInventory().setItemInMainHand(null);
            } else {
                player.getInventory().setItemInOffHand(null);
            }
            return false;
        }

        damageable.setDamage(newDamage);
        rod.setItemMeta(meta);
        updateRodInInventory(player, rod);
        return true;
    }

    private void updateRodInInventory(Player player, ItemStack rod) {
        if (player.getInventory().getItemInMainHand().getType() == Material.FISHING_ROD) {
            player.getInventory().setItemInMainHand(rod);
        } else if (player.getInventory().getItemInOffHand().getType() == Material.FISHING_ROD) {
            player.getInventory().setItemInOffHand(rod);
        }
    }

    private boolean isRodDurabilityLow(Player player) {
        ItemStack rod = getHeldFishingRod(player);
        if (rod == null) {
            return true;
        }
        ItemMeta meta = rod.getItemMeta();
        if (meta instanceof Damageable damageable) {
            int remaining = rod.getType().getMaxDurability() - damageable.getDamage();
            return remaining <= LOW_DURABILITY_THRESHOLD;
        }
        return false;
    }

    // ==========================================
    // AFK SELF-DEFENSE CONTROL METHODS
    // ==========================================

    public void startAutoDefense(Player player) {
        if (activeDefenseTasks.containsKey(player.getUniqueId())) return;
        AutoDefenseTask defenseTask = new AutoDefenseTask(HereFishyPlugin.getInstance(), player);
        activeDefenseTasks.put(player.getUniqueId(), defenseTask);
        defenseTask.runTaskTimer(HereFishyPlugin.getInstance(), 0L, 1L);
        if (!isAutoFishing(player)) {
            player.sendMessage(Component.text("⚔ AFK Auto-defense activated! Stand still; we will protect you. Move manually to deactivate.").color(NamedTextColor.GOLD));
        }
    }

    public void stopAutoDefense(Player player, boolean silent) {
        AutoDefenseTask defenseTask = activeDefenseTasks.remove(player.getUniqueId());
        if (defenseTask != null) {
            defenseTask.cancel();
            if (!silent && !isAutoFishing(player)) {
                player.sendMessage(Component.text("⚔ AFK Auto-defense deactivated.").color(NamedTextColor.YELLOW));
            }
        }
    }

    public boolean hasAutoDefense(Player player) {
        return activeDefenseTasks.containsKey(player.getUniqueId());
    }

    public AutoDefenseTask getAutoDefenseTask(Player player) {
        return activeDefenseTasks.get(player.getUniqueId());
    }

    public void cancelAllDefenseTasks() {
        for (AutoDefenseTask task : activeDefenseTasks.values()) {
            task.cancel();
        }
        activeDefenseTasks.clear();
    }

    private boolean hasActiveDefenseTarget(Player player) {
        Location loc = player.getLocation();
        if (loc.getWorld() == null) return false;
        double attackRadius = 4.0;
        for (org.bukkit.entity.Entity entity : loc.getWorld().getNearbyEntities(loc, attackRadius, attackRadius, attackRadius)) {
            if (entity instanceof Monster ||
                entity instanceof Slime ||
                entity instanceof Phantom ||
                entity instanceof Spider) {
                if (entity instanceof org.bukkit.entity.LivingEntity living) {
                    if (!living.isDead() && player.hasLineOfSight(living)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        stopAutoFishing(player);
        stopAutoDefense(player, true);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (hasAutoDefense(player) && !isAutoFishing(player)) {
            AutoDefenseTask defenseTask = getAutoDefenseTask(player);
            if (defenseTask != null) {
                Location expected = defenseTask.getExpectedTeleportLocation();

                // If it's a plugin-driven teleport/rotation, ignore it
                if (expected != null && expected.getWorld() == event.getTo().getWorld()) {
                    double distSq = expected.distanceSquared(event.getTo());
                    // A tiny threshold handles floating-point inaccuracies
                    if (distSq < 0.05) {
                        defenseTask.clearExpectedTeleportLocation();
                        return;
                    }
                }

                // Check if they actually moved (e.g. moved X, Y, or Z by > 0.05 blocks)
                Location from = event.getFrom();
                Location to = event.getTo();
                if (from.getWorld() != to.getWorld() || 
                    Math.abs(from.getX() - to.getX()) > 0.05 || 
                    Math.abs(from.getY() - to.getY()) > 0.05 || 
                    Math.abs(from.getZ() - to.getZ()) > 0.05) {

                    // Manual movement detected! Stop auto defense
                    stopAutoDefense(player, false);
                }
            }
        }
    }
}
