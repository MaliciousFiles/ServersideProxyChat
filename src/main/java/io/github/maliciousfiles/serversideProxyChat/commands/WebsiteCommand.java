package io.github.maliciousfiles.serversideProxyChat.commands;

import io.github.maliciousfiles.serversideProxyChat.VoiceServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Color;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WebsiteCommand extends ProxyChatCommand{
    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        String link = "https://"+VoiceServer.ip+":"+MinecraftServer.getServer().getPort();

        commandSender.sendMessage(Component.text(link)
                .clickEvent(ClickEvent.openUrl(link))
                .color(TextColor.color(38, 99, 238))
                .decorate(TextDecoration.UNDERLINED));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        return List.of();
    }
}
