package io.github.maliciousfiles.serversideProxyChat.commands;

import io.github.maliciousfiles.serversideProxyChat.websocket.WebSocketServer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DisconnectCommand extends ProxyChatCommand {

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        Player target = commandSender instanceof Player player ? player : null;

        boolean self = true;
        if (strings.length > 0) {
            if (!commandSender.hasPermission(DISCONNECT_OTHERS_PERM)) {
                return error(commandSender, "You do not have permission to disconnect others");
            }

            target = commandSender.getServer().getPlayer(strings[0]);
            self = false;
        }

        if (target == null) {
            return error(commandSender, self
                    ? "You must be a player to disconnect yourself"
                    : "Player not found");
        }

        if (!WebSocketServer.isRegistered(target)) {
            return error(commandSender, "User is not connected to voice chat");
        }

        WebSocketServer.disconnect(target);
        return success(commandSender, self
                ? "You are now disconnected from voice chat"
                : target.getName() + " is now disconnected from voice chat");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        return strings.length == 1 && commandSender.hasPermission(DISCONNECT_OTHERS_PERM)
                ? Bukkit.getOnlinePlayers().stream().filter(WebSocketServer::isRegistered).filter(p->!p.equals(commandSender)).map(Player::getName).filter(st->st.toLowerCase().startsWith(strings[strings.length-1].toLowerCase())).sorted().toList()
                : List.of();
    }
}
