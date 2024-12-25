package io.github.maliciousfiles.serversideProxyChat.commands;

import io.github.maliciousfiles.serversideProxyChat.websocket.WebSocketPacket;
import io.github.maliciousfiles.serversideProxyChat.websocket.WebSocketServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MuteCommand extends ProxyChatCommand {

    private final boolean mute;
    public MuteCommand(boolean mute) {
        this.mute = mute;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        Player target = commandSender instanceof Player player ? player : null;

        boolean self = true;
        if (strings.length > 0) {
            if (!commandSender.hasPermission(MUTE_OTHERS_PERM)) {
                return error(commandSender, "You do not have permission to "+(mute ? "mute" : "unmute")+" others");
            }

            target = commandSender.getServer().getPlayer(strings[0]);
            self = false;
        }

        if (target == null) {
            return error(commandSender, self
                    ? "You must be a player to "+(mute ? "mute" : "unmute")+" yourself"
                    : "Player not found");
        }

        if (!WebSocketServer.isRegistered(target)) {
            return error(commandSender, "User is not connected to voice chat");
        }

        boolean isForceMuted = WebSocketServer.isForceMuted(target);
        if (!self) {
            if (isForceMuted == mute) {
                return error(commandSender, target.getName() + " is already "+(mute ? "muted" : "unmuted"));
            }
            WebSocketServer.forceMute(target, mute);

            return success(commandSender, target.getName() + " is now "+(mute ? "force muted" : "unmuted"));
        } else {
            if (isForceMuted) {
                return error(commandSender, "You are force muted, you cannot "+(mute ? "mute" : "unmute")+" yourself");
            }
            if (WebSocketServer.isSelfMuted(target) == mute) {
                return error(commandSender, "You are already "+(mute ? "muted" : "unmuted"));
            }

            WebSocketServer.muteSelf(target, mute);
            return success(commandSender, "You are now "+(mute ? "muted" : "unmuted"));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        return strings.length == 1 && commandSender.hasPermission(MUTE_OTHERS_PERM)
                ? Bukkit.getOnlinePlayers().stream().filter(p->WebSocketServer.isForceMuted(p) != mute).filter(p->!mute || !p.equals(commandSender)).map(Player::getName).filter(st->st.toLowerCase().startsWith(strings[strings.length-1].toLowerCase())).sorted().toList()
                : List.of();
    }
}
