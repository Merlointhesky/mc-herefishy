package com.herefishy.herefishy.listener;

import com.herefishy.herefishy.HereFishyPlugin;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.aurelium.auraskills.api.user.SkillsUser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class FishingListener implements Listener {

    private static final int LOW_DURABILITY_THRESHOLD = 5;

    private final Set<UUID> autoFishingPlayers = new HashSet<>();

    public boolean isAutoFishing(Player player) {
        return autoFishingPlayers.contains(player.getUniqueId());
    }

    public void startAutoFishing(Player player) {
        autoFishingPlayers.add(player.getUniqueId());
    }

    public void stopAutoFishing(Player player) {
        autoFishingPlayers.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (!isAutoFishing(player)) {
            return;
        }

        switch (event.getState()) {
            case BITE -> {
                // Generate loot from vanilla fishing loot table
                giveFishingLoot(player);

                // Award fishing XP first (vanilla: 1-6 XP per catch)
                int xpAwarded = 1 + RANDOM.nextInt(6);
                player.giveExp(xpAwarded);

                // Damage the fishing rod on catch (pass XP for Mending simulation)
                ItemStack rod = getHeldFishingRod(player);
                if (rod != null) {
                    if (!damageFishingRod(player, rod, xpAwarded)) {
                        stopAutoFishing(player);
                        player.sendMessage(Component.text("Auto-fishing stopped — your fishing rod broke!")
                                .color(NamedTextColor.RED));
                        event.getHook().remove();
                        return;
                    }
                }

                // Remove the hook entity
                FishHook hook = event.getHook();
                if (hook != null && !hook.isDead()) {
                    hook.remove();
                }

                // Schedule re-cast
                scheduleReCast(player);
            }

            case CAUGHT_FISH -> {
                // Safety fallback: should not fire for auto-fishing players
                // since we remove the hook on BITE, but handle it just in case
                ItemStack rod = getHeldFishingRod(player);
                if (rod != null) {
                    if (!damageFishingRod(player, rod, 0)) {
                        stopAutoFishing(player);
                        player.sendMessage(Component.text("Auto-fishing stopped — your fishing rod broke!")
                                .color(NamedTextColor.RED));
                        return;
                    }
                }
                scheduleReCast(player);
            }

            case FAILED_ATTEMPT, IN_GROUND -> {
                scheduleReCast(player);
            }

            default -> {
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        autoFishingPlayers.remove(event.getPlayer().getUniqueId());
    }

    private static final Random RANDOM = new Random();

    // Vanilla fishing loot categories (approximate vanilla weights)
    private static final List<Material> FISH_LOOT = List.of(
            Material.COD, Material.COD, Material.COD, Material.COD,  // 60% cod
            Material.SALMON, Material.SALMON,                          // 25% salmon
            Material.TROPICAL_FISH,                                    // 10% tropical fish
            Material.PUFFERFISH                                       // 5% pufferfish
    );

    private static final List<Material> JUNK_LOOT = List.of(
            Material.BOWL, Material.LEATHER, Material.LEATHER_BOOTS,
            Material.ROTTEN_FLESH, Material.STICK, Material.STRING,
            Material.POTION, Material.BONE, Material.TRIPWIRE_HOOK,
            Material.INK_SAC
    );

    private static final List<Material> TREASURE_LOOT = List.of(
            Material.ENCHANTED_BOOK,  // Enchanted book
            Material.BOW,             // Enchanted bow
            Material.FISHING_ROD,     // Enchanted fishing rod
            Material.LILY_PAD,
            Material.NAME_TAG,
            Material.SADDLE,
            Material.NAUTILUS_SHELL
    );

    private void giveFishingLoot(Player player) {
        ItemStack loot = generateFishingLoot(player);
        if (loot == null || loot.getType() == Material.AIR) return;

        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(loot);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItem(player.getLocation(), drop);
        }

        // Send colored catch message
        sendCatchMessage(player, loot);

        // Award AuraSkills fishing XP if available
        awardAuraSkillsFishingXp(player, loot);
    }

    private void sendCatchMessage(Player player, ItemStack loot) {
        String itemName = formatItemName(loot.getType().name());
        NamedTextColor color = getLootColor(loot.getType());

        Component message = Component.text("Here fishy! Got ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(itemName).color(color))
                .append(Component.text(".").color(NamedTextColor.GRAY));

        player.sendMessage(message);
    }

    private String formatItemName(String materialName) {
        String[] words = materialName.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(" ");
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private NamedTextColor getLootColor(Material material) {
        return switch (material) {
            case ENCHANTED_BOOK, BOW, FISHING_ROD, SADDLE, NAME_TAG, NAUTILUS_SHELL -> NamedTextColor.GREEN; // Treasure
            case LILY_PAD -> NamedTextColor.YELLOW; // Semi-rare
            case LEATHER_BOOTS, BOWL, STICK, STRING, BONE, ROTTEN_FLESH, TRIPWIRE_HOOK, INK_SAC, POTION, LEATHER -> NamedTextColor.RED; // Junk
            default -> NamedTextColor.YELLOW; // Fish
        };
    }

    private void awardAuraSkillsFishingXp(Player player, ItemStack loot) {
        if (Bukkit.getPluginManager().getPlugin("AuraSkills") == null) {
            return; // AuraSkills not installed
        }
        try {
            AuraSkillsApi auraSkills = AuraSkillsApi.get();
            SkillsUser user = auraSkills.getUser(player.getUniqueId());
            if (user != null) {
                // Dynamic XP based on loot rarity
                double xpAmount = calculateFishingXp(loot);
                user.addSkillXp(Skills.FISHING, xpAmount);
            }
        } catch (Exception e) {
            // AuraSkills integration failed, but auto-fishing continues
        }
    }

    private double calculateFishingXp(ItemStack loot) {
        return switch (loot.getType()) {
            case ENCHANTED_BOOK, BOW, FISHING_ROD, SADDLE, NAME_TAG, NAUTILUS_SHELL -> 50.0; // Treasure
            case LILY_PAD -> 35.0; // Semi-rare
            case LEATHER_BOOTS, BOWL, STICK, STRING, BONE, ROTTEN_FLESH, TRIPWIRE_HOOK, INK_SAC, POTION, LEATHER -> 15.0; // Junk
            default -> 25.0; // Fish (cod, salmon, tropical fish, pufferfish)
        };
    }

    private ItemStack generateFishingLoot(Player player) {
        double roll = RANDOM.nextDouble();

        // Check for Luck of the Sea enchantment
        int luckLevel = getLuckLevel(player);

        // Vanilla weights: Fish ~85%, Treasure ~5%, Junk ~10%
        // With Luck of the Sea: Treasure increases, Junk decreases
        double treasureChance = 0.05 + (luckLevel * 0.01);
        double junkChance = 0.10 - (luckLevel * 0.005);
        double fishChance = 1.0 - treasureChance - junkChance;

        if (roll < treasureChance) {
            return generateTreasureLoot();
        } else if (roll < treasureChance + junkChance) {
            return generateJunkLoot();
        } else {
            return generateFishLoot();
        }
    }

    private int getLuckLevel(Player player) {
        ItemStack rod = getHeldFishingRod(player);
        if (rod == null || !rod.hasItemMeta()) return 0;
        return rod.getItemMeta().getEnchantLevel(Enchantment.LUCK_OF_THE_SEA);
    }

    private ItemStack generateFishLoot() {
        Material fish = FISH_LOOT.get(RANDOM.nextInt(FISH_LOOT.size()));
        return new ItemStack(fish, 1);
    }

    private ItemStack generateJunkLoot() {
        Material junk = JUNK_LOOT.get(RANDOM.nextInt(JUNK_LOOT.size()));
        ItemStack item = new ItemStack(junk, 1);

        // Damaged leather boots
        if (junk == Material.LEATHER_BOOTS && item.getItemMeta() instanceof Damageable damageable) {
            damageable.setDamage((int) (Material.LEATHER_BOOTS.getMaxDurability() * RANDOM.nextDouble()));
            item.setItemMeta(damageable);
        }

        return item;
    }

    private ItemStack generateTreasureLoot() {
        Material treasure = TREASURE_LOOT.get(RANDOM.nextInt(TREASURE_LOOT.size()));
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
                    // Add random fishing-related enchantments
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

    private void scheduleReCast(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                if (!isAutoFishing(player)) return;
                if (!isHoldingFishingRod(player)) {
                    stopAutoFishing(player);
                    return;
                }

                if (isInventoryFull(player)) {
                    stopAutoFishing(player);
                    player.sendMessage(Component.text("Auto-fishing stopped — inventory full!")
                            .color(NamedTextColor.RED));
                    return;
                }

                if (isRodDurabilityLow(player)) {
                    stopAutoFishing(player);
                    player.sendMessage(Component.text("Auto-fishing stopped — fishing rod is nearly broken!")
                            .color(NamedTextColor.RED));
                    return;
                }

                Vector direction = player.getLocation().getDirection();
                FishHook newHook = player.launchProjectile(FishHook.class, direction.multiply(0.8));
                if (newHook != null) {
                    newHook.setVelocity(direction.multiply(0.8));
                }
            }
        }.runTaskLater(HereFishyPlugin.getInstance(), 15L);
    }

    private boolean isHoldingFishingRod(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        return mainHand.getType() == Material.FISHING_ROD || offHand.getType() == Material.FISHING_ROD;
    }

    private ItemStack getHeldFishingRod(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType() == Material.FISHING_ROD) return mainHand;
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand.getType() == Material.FISHING_ROD) return offHand;
        return null;
    }

    private boolean damageFishingRod(Player player, ItemStack rod, int xpAwarded) {
        ItemMeta meta = rod.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return true;

        // Check for Mending enchantment (uses XP to repair)
        Enchantment mending = Enchantment.getByKey(NamespacedKey.minecraft("mending"));
        if (mending != null && meta.hasEnchant(mending) && xpAwarded > 0 && damageable.getDamage() > 0) {
            // Mending: 2 XP = 1 durability repaired
            int durabilityRepaired = xpAwarded / 2;
            int newDamage = Math.max(0, damageable.getDamage() - durabilityRepaired);
            damageable.setDamage(newDamage);
            rod.setItemMeta(meta);
            updateRodInInventory(player, rod);
            return true; // Mending absorbed the "damage" by repairing
        }

        // Check for Unbreaking enchantment (reduces damage chance)
        Enchantment unbreaking = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
        int unbreakingLevel = (unbreaking != null && meta.hasEnchant(unbreaking)) ? meta.getEnchantLevel(unbreaking) : 0;
        if (unbreakingLevel > 0) {
            // Unbreaking: Level 1 = 50% chance to ignore damage, Level 2 = 66%, Level 3 = 75%
            double chanceToIgnoreDamage = unbreakingLevel / (unbreakingLevel + 1.0);
            if (RANDOM.nextDouble() < chanceToIgnoreDamage) {
                return true; // No damage taken this time
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
        if (rod == null) return true;
        ItemMeta meta = rod.getItemMeta();
        if (meta instanceof Damageable damageable) {
            int remaining = rod.getType().getMaxDurability() - damageable.getDamage();
            return remaining <= LOW_DURABILITY_THRESHOLD;
        }
        return false;
    }

    private boolean isInventoryFull(Player player) {
        return player.getInventory().firstEmpty() == -1;
    }
}
