package io.github.maliciousfiles.serversideProxyChat.commands;

import io.github.maliciousfiles.serversideProxyChat.websocket.WebSocketServer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PrivateChannelCommand extends ProxyChatCommand {
    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        if (strings.length < 1 || !(strings[0].equalsIgnoreCase("join") || strings[0].equalsIgnoreCase("leave"))) {
            return error(commandSender, "Must specify join or leave");
        }

        String channel = null;
        if (strings[0].equalsIgnoreCase("join")) {
            if (strings.length < 2) {
                return error(commandSender, "Must specify a channel to join");
            }
            channel = strings[1].toLowerCase();
        }

        Player target = commandSender instanceof Player player ? player : null;
        if (strings[0].equalsIgnoreCase("join")) {
            if (strings.length >= 3) {
                if (!commandSender.hasPermission(PRIVATE_CHANNEL_MANAGE_OTHERS_PERM)) {
                    return error(commandSender, "You do not have permission to manage other players");
                }
                target = Bukkit.getPlayer(strings[2]);
                if (target == null) {
                    return error(commandSender, "Player not found");
                }
            }
        } else if (strings[0].equalsIgnoreCase("leave")) {
            if (strings.length >= 2) {
                if (!commandSender.hasPermission(PRIVATE_CHANNEL_MANAGE_OTHERS_PERM)) {
                    return error(commandSender, "You do not have permission to manage other players");
                }
                target = Bukkit.getPlayer(strings[1]);
                if (target == null) {
                    return error(commandSender, "Player not found");
                }
            }
        }
        if (target == null) {
            return error(commandSender, "Must be a player to manage your own channel");
        }
        if (!WebSocketServer.isRegistered(target)) {
            return error(commandSender, "User is not connected to voice chat");
        }

        boolean self = target.equals(commandSender);
        if (channel != null) {
            if (channel.equals(WebSocketServer.getPrivateChannel(target))) {
                return error(commandSender, "Already in channel '" + channel + "'");
            }

            boolean exists = WebSocketServer.getPrivateChannels().contains(channel);
            WebSocketServer.joinPrivateChannel(target, channel);

            return success(commandSender, self
                    ? (!exists ? "Created and j" : "J") + "oined private channel '" + channel + "'"
                    : (!exists ? "Created and a" : "A") + "dded " + target.getName() + " to private channel '" + channel + "'");
        } else {
            if (WebSocketServer.getPrivateChannel(target) == null) {
                return error(commandSender, "Not in a private channel");
            }

            WebSocketServer.leavePrivateChannel(target);

            return success(commandSender, self
                    ? "Left private channel"
                    : "Removed " + target.getName() + " from private channel");
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        List<String> completions = List.of();
        if (!commandSender.hasPermission(PRIVATE_CHANNEL_MANAGE_SELF_PERM)) return completions;

        if (strings.length == 1) {
            completions = List.of("join", "leave");
        } else if (strings.length == 2) {
            if (strings[0].equalsIgnoreCase("join")) {
                completions = WebSocketServer.getPrivateChannels();
            } else if (strings[0].equalsIgnoreCase("leave")) {
                if (commandSender.hasPermission(PRIVATE_CHANNEL_MANAGE_OTHERS_PERM)) {
                    completions = Bukkit.getOnlinePlayers().stream()
                            .filter(WebSocketServer::isRegistered)
                            .filter(p -> WebSocketServer.getPrivateChannel(p) != null)
                            .map(Player::getName).toList();
                }
            }
        } else if (strings.length == 3) {
            if (strings[0].equalsIgnoreCase("join")) {
                if (commandSender.hasPermission(PRIVATE_CHANNEL_MANAGE_OTHERS_PERM)) {
                    completions = Bukkit.getOnlinePlayers().stream()
                            .filter(WebSocketServer::isRegistered)
                            .filter(p -> !strings[1].equalsIgnoreCase(WebSocketServer.getPrivateChannel(p)))
                            .map(Player::getName).toList();
                }
            }
        }

        return completions.stream().filter(st->st.toLowerCase().startsWith(strings[strings.length-1].toLowerCase())).sorted().toList();
    }
}
