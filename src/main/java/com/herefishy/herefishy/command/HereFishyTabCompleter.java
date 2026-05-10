package com.herefishy.herefishy.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class HereFishyTabCompleter implements TabCompleter {

    private static final List<String> SUBS = List.of("start", "stop", "config");

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return SUBS.stream()
                    .filter(s -> prefix.isEmpty() || s.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
