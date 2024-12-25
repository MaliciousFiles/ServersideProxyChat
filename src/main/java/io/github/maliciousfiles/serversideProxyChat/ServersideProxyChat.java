package io.github.maliciousfiles.serversideProxyChat;

import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;

public final class ServersideProxyChat extends JavaPlugin {

    private static ServersideProxyChat instance;
    public static ServersideProxyChat getInstance() { return instance; }

    @Override
    public void onEnable() {
        instance = this;

        VoiceServer.start();
    }

    @Override
    public void onDisable() {
        VoiceServer.stop();
    }
}
