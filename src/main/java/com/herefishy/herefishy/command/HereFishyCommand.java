package com.herefishy.herefishy.command;

import com.herefishy.herefishy.gui.RoutingConfigMenu;
import com.herefishy.herefishy.listener.FishingListener;
import com.herefishy.herefishy.session.FishySessionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class HereFishyCommand implements CommandExecutor {

    private final FishingListener fishingListener;
    private final FishySessionManager sessionManager;

    public HereFishyCommand(FishingListener fishingListener, FishySessionManager sessionManager) {
        this.fishingListener = fishingListener;
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players!")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            usage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (fishingListener.isAutoFishing(player)) {
                    player.sendMessage(Component.text("Auto-fishing is already enabled!")
                            .color(NamedTextColor.YELLOW));
                } else {
                    fishingListener.startAutoFishing(player);
                    player.sendMessage(Component.text("Auto-fishing enabled! Cast your rod to begin.")
                            .color(NamedTextColor.GREEN));
                }
            }
            case "stop" -> {
                if (!fishingListener.isAutoFishing(player)) {
                    player.sendMessage(Component.text("Auto-fishing is not enabled!")
                            .color(NamedTextColor.YELLOW));
                } else {
                    fishingListener.stopAutoFishing(player);
                    player.sendMessage(Component.text("Auto-fishing disabled.")
                            .color(NamedTextColor.GREEN));
                }
            }
            case "config" -> RoutingConfigMenu.open(player, sessionManager);
            default -> usage(player);
        }

        return true;
    }

    private static void usage(Player player) {
        player.sendMessage(Component.text("Usage: /herefishy <start|stop|config>")
                .color(NamedTextColor.YELLOW));
    }
}
