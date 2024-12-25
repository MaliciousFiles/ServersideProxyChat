package io.github.maliciousfiles.serversideProxyChat.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public abstract class ProxyChatCommand implements CommandExecutor, TabCompleter {
    protected static final String MUTE_OTHERS_PERM = "serversideproxychat.mute_others";
    protected static final String DISCONNECT_OTHERS_PERM = "serversideproxychat.disconnect_others";

    protected static boolean error(CommandSender sender, String error) {
        sender.sendMessage(Component.text(error)
                .color(NamedTextColor.RED));
        return true;
    }

    protected static boolean success(CommandSender sender, String success) {
        sender.sendMessage(Component.text(success)
                .color(NamedTextColor.GREEN));
        return true;
    }
}
