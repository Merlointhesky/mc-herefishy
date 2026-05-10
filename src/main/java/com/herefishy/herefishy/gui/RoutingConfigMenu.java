package com.herefishy.herefishy.gui;

import com.herefishy.herefishy.loot.FishingLootTable;
import com.herefishy.herefishy.loot.LootClassifier;
import com.herefishy.herefishy.session.DepositBucket;
import com.herefishy.herefishy.session.FishySession;
import com.herefishy.herefishy.session.FishySessionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class RoutingConfigMenu {

    public static final Component TITLE = Component.text("HereFishy — Routing");

    private RoutingConfigMenu() {
    }

    public static void open(Player player, FishySessionManager sessionManager) {
        FishySession session = sessionManager.session(player);
        List<Material> materials = FishingLootTable.configurableMaterialsOrdered();
        int rows = Math.min(6, Math.max(1, (int) Math.ceil(materials.size() / 9.0)));
        int size = rows * 9;
        Inventory inventory = Bukkit.createInventory(null, size, TITLE);
        int index = 0;
        for (Material material : materials) {
            if (index >= size) {
                break;
            }
            inventory.setItem(index++, iconFor(session, material));
        }
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Component.text(" ").color(NamedTextColor.DARK_GRAY));
            filler.setItemMeta(fillerMeta);
        }
        while (index < size) {
            inventory.setItem(index++, filler.clone());
        }
        player.openInventory(inventory);
    }

    public static ItemStack iconFor(FishySession session, Material material) {
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) {
            return icon;
        }
        DepositBucket bucket = LootClassifier.depositBucket(material, session);
        NamedTextColor heading = bucket == DepositBucket.TREASURE ? NamedTextColor.GOLD : NamedTextColor.RED;
        meta.displayName(Component.text(prettyName(material))
                .color(heading)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Destination: " + bucket.name())
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Click to toggle treasure ↔ junk routing.")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("(Fish always go to the fish chest)")
                .color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private static String prettyName(Material material) {
        String raw = material.name().toLowerCase().replace('_', ' ');
        String[] parts = raw.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            if (!part.isEmpty()) {
                builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return builder.toString();
    }
}
