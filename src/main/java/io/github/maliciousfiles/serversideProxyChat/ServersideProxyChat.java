package io.github.maliciousfiles.serversideProxyChat;

import io.github.maliciousfiles.serversideProxyChat.commands.DisconnectCommand;
import io.github.maliciousfiles.serversideProxyChat.commands.MuteCommand;
import io.github.maliciousfiles.serversideProxyChat.commands.PrivateChannelCommand;
import io.github.maliciousfiles.serversideProxyChat.commands.ProxyChatCommand;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;

public final class ServersideProxyChat extends JavaPlugin {

    private static ServersideProxyChat instance;
    public static ServersideProxyChat getInstance() { return instance; }

    private void registerCommand(String command, ProxyChatCommand handler) {
        getCommand(command).setExecutor(handler);
        getCommand(command).setTabCompleter(handler);
    }

    @Override
    public void onEnable() {
        instance = this;

        registerCommand("mute", new MuteCommand(true));
        registerCommand("unmute", new MuteCommand(false));
        registerCommand("disconnect", new DisconnectCommand());
        registerCommand("private-channel", new PrivateChannelCommand());

        VoiceServer.start();
    }

    @Override
    public void onDisable() {
        VoiceServer.stop();
    }
}
