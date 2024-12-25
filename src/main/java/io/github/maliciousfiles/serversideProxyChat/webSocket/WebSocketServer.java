package io.github.maliciousfiles.serversideProxyChat.webSocket;

import io.github.maliciousfiles.serversideProxyChat.ServersideProxyChat;
import io.github.maliciousfiles.serversideProxyChat.util.BidiEntry;
import io.github.maliciousfiles.serversideProxyChat.util.Triple;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class WebSocketServer extends SimpleChannelInboundHandler<WebSocketPacket> {
    private static final Map<String, WebSocketServer> webSockets = new HashMap<>(); // map of IDs to handlers for that channel
    private static final Map<UUID, String> players = new HashMap<>(); // map of player to ID
    private static final List<BidiEntry<String, String>> connected = new ArrayList<>(); // list of players with P2P connections; ID <=> ID

    // we need some Minecraft events to calculate volumes
    public static void initListener() {
        Bukkit.getPluginManager().registerEvents(new MinecraftListener(), ServersideProxyChat.getInstance());
    }

    // close all channels
    public static void unload() {
        for (Map.Entry<String, WebSocketServer> entry : webSockets.entrySet()) {
            WebSocketServer handler = entry.getValue();
            handler.close(handler.channel);
        }
    }

    /**
     * Does not check if `id` is valid.
     */
    // remove ALL connections to or from the given ID; erases them
    private static void removeAllPeers(String id) {
        for (Map.Entry<String, WebSocketServer> entry : webSockets.entrySet()) {
            connected.remove(new BidiEntry<>(id, entry.getKey()));

            entry.getValue().channel.writeAndFlush(new WebSocketPacket.RemovePeer(id));
            webSockets.get(id).channel.writeAndFlush(new WebSocketPacket.RemovePeer(entry.getKey()));
        }
    }

    /**
     * Checks validity of the given UUID.
     */
    // calculate and send volume updates for the given player to all connected players
    private static void calculateVolume(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        String id = players.get(uuid);

        if (player != null && id != null) {
            for (Player other : player.getWorld().getPlayers()) {
                String otherId = players.get(other.getUniqueId());

                if (otherId != null) {
                    double distance = player.getLocation().distance(other.getLocation());

                    // TODO: calculate volumes goodly
                    setVolume(id, otherId, Math.max(0, Math.min(1, 1 - (distance / 50))));
                }
            }
        }
    }

    private String id;
    private UUID player;
    private Channel channel;

    public WebSocketServer() {}

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketPacket msg) {
        channel = ctx.channel();

        if (msg instanceof WebSocketPacket.Close) { // they want to exit
            exit(ctx);
        } else if (msg instanceof WebSocketPacket.Validate(String iceID)) { // setting up the ID
            this.id = iceID;

            if (webSockets.containsKey(id)) {
                close(ctx.channel());
                return;
            }

            webSockets.put(id, this);
        } else if (msg instanceof WebSocketPacket.Join join) { // selecting a player
            Bukkit.getScheduler().runTaskAsynchronously(ServersideProxyChat.getInstance(), () -> {
                try {
                    join(ctx, join);
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
        } else if (msg instanceof WebSocketPacket.RelayICECandidate relayICE) { // ICE
            relayICECandidate(relayICE);
        } else if (msg instanceof WebSocketPacket.RelaySessionDescription relaySess) { // ICE
            relaySessionDescription(relaySess);
        } else if (msg instanceof WebSocketPacket.VolumeVisualization volume) { // providing a volume visualization
            displayVolumeVisualization(volume);
        }

        if (msg instanceof WebSocketPacket.Validate || msg instanceof WebSocketPacket.RequestPlayerList) { // need to give them the player list
            ctx.writeAndFlush(new WebSocketPacket.PlayerList().players(Bukkit.getOnlinePlayers()));
        }
    }

    // show the player the volume visualization, if there isn't another action bar
    private void displayVolumeVisualization(WebSocketPacket.VolumeVisualization volume) {
        Player onlinePlayer = Bukkit.getPlayer(player);
        if (onlinePlayer == null) return;
        if (!volume.actionBar().matches("[▁▂▃▄▅▆▇█]+")) return;

        onlinePlayer.sendActionBar(Component.text(volume.actionBar()).color(NamedTextColor.GREEN));
    }

    // forcibly terminate the web socket connection
    private void close(Channel channel) {
        channel.writeAndFlush(new CloseWebSocketFrame());
        channel.close();
    }

    // destroy all information associated with this handler; exit
    private void exit(ChannelHandlerContext ctx) {
        if (id != null && webSockets.containsKey(id)) {
            removeAllPeers(id);
            webSockets.remove(id);
            if (player != null) players.remove(player);
        }

        close(ctx.channel());
    }

    // idk, just pass it along
    private void relaySessionDescription(WebSocketPacket.RelaySessionDescription relaySess) {
        if (webSockets.containsKey(relaySess.peer_id())) {
            webSockets.get(relaySess.peer_id()).channel.writeAndFlush(
                    new WebSocketPacket.SessionDescription(id, relaySess.session_description())
            );
        }
    }

    // idk, just pass it along
    private void relayICECandidate(WebSocketPacket.RelayICECandidate relayICE) {
        if (webSockets.containsKey(relayICE.peer_id())) {
            webSockets.get(relayICE.peer_id()).channel.writeAndFlush(
                    new WebSocketPacket.ICECandidate(id, relayICE.ice_candidate())
            );
        }
    }

    // verify the player in game, SHOULD BE RUN AS AN ASYNC TASK
    private void join(ChannelHandlerContext ctx, WebSocketPacket.Join join) throws InterruptedException, ExecutionException {
        OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(join.uuid()));

        AtomicReference<WebSocketPacket.JoinVerify.VerifyResponse> response = new AtomicReference<>();

        if (players.containsKey(player.getUniqueId())) { // already taken
            ctx.writeAndFlush(WebSocketPacket.JoinVerify.of(WebSocketPacket.JoinVerify.VerifyResponse.TAKEN, player.getName()));
            return;
        } else if (!player.isOnline()) { // not online
            response.set(WebSocketPacket.JoinVerify.VerifyResponse.NOT_FOUND);
        } else { // ask the player
            String yes = "/" + UUID.randomUUID();
            String no = "/" + UUID.randomUUID();

            player.getPlayer().sendMessage(
                    Component.text("\n\n\nConfirm proximity voice chat connection\n\n    ")
                            .append(Component.text("YES")
                                    .color(NamedTextColor.GREEN)
                                    .decorate(TextDecoration.BOLD)
                                    .clickEvent(ClickEvent.runCommand(yes))
                                    .hoverEvent(HoverEvent.showText(Component.text("Confirm").color(NamedTextColor.DARK_GREEN)))
                            .append(Component.text("   "))
                            .append(Component.text("NO")
                                    .color(NamedTextColor.RED)
                                    .decorate(TextDecoration.BOLD)
                                    .clickEvent(ClickEvent.runCommand(no))
                                    .hoverEvent(HoverEvent.showText(Component.text("Deny").color(NamedTextColor.DARK_RED))))
                            .append(Component.text("\n"))));

            CompletableFuture<Boolean> future = MinecraftListener.waitForResponse(player.getUniqueId(), yes, no)
                    .completeOnTimeout(null,60, TimeUnit.SECONDS);

            Boolean completed = future.get();
            if (completed == null) {
                player.getPlayer().sendMessage(
                        Component.text("Proximity voice chat request timed out.")
                                .color(NamedTextColor.GRAY));
                response.set(WebSocketPacket.JoinVerify.VerifyResponse.TIMED_OUT);
            } else if (completed) {
                player.getPlayer().sendMessage(
                        Component.text("Proximity voice chat request validated.")
                                .color(NamedTextColor.GREEN));
                response.set(WebSocketPacket.JoinVerify.VerifyResponse.ACCEPTED);
            } else {
                player.getPlayer().sendMessage(
                        Component.text("Proximity voice chat request denied.")
                                .color(NamedTextColor.RED));
                response.set(WebSocketPacket.JoinVerify.VerifyResponse.NOT_ACCEPTED);
            }
        }

        ctx.writeAndFlush(WebSocketPacket.JoinVerify.of(response.get(), player.getName()));

        // if we successfully verified, setup volume and register player
        if (response.get().verified) {
            calculateVolume(player.getUniqueId());
            players.put(player.getUniqueId(), id);
            this.player = player.getUniqueId();
        }
    }

    // set the volume between two IDs
    private static void setVolume(String id1, String id2, double volume) {
        if (!id1.equals(id2) && webSockets.containsKey(id1) && webSockets.containsKey(id2)) {
            BidiEntry<String, String> entry = new BidiEntry<>(id1, id2);

            if (volume > 0) {
                // add peers if they weren't previously connected
                if (!connected.contains(entry)) {
                    connected.add(entry);

                    webSockets.get(id1).channel.writeAndFlush(new WebSocketPacket.AddPeer(id2, false));
                    webSockets.get(id2).channel.writeAndFlush(new WebSocketPacket.AddPeer(id1, true));
                }

                // set the volumes
                webSockets.get(id1).channel.writeAndFlush(new WebSocketPacket.SetVolume().volume(id2, volume));
                webSockets.get(id2).channel.writeAndFlush(new WebSocketPacket.SetVolume().volume(id1, volume));
            } else {
                if (connected.contains(entry)) { // remove peers if volume <= 0
                    connected.remove(entry);

                    webSockets.get(id1).channel.writeAndFlush(new WebSocketPacket.RemovePeer(id2));
                    webSockets.get(id2).channel.writeAndFlush(new WebSocketPacket.RemovePeer(id1));
                }
            }
        }
    }

    private static class MinecraftListener implements Listener {
        private static final Map<UUID, Triple<String, String, CompletableFuture<Boolean>>> questions = new HashMap<>();

        private static CompletableFuture<Boolean> waitForResponse(UUID uuid, String yes, String no) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            questions.put(uuid, Triple.of(yes, no, future));

            return future;
        }

        @EventHandler
        public void onResponse(PlayerCommandPreprocessEvent e) {
            if (!questions.containsKey(e.getPlayer().getUniqueId())) return;
            Triple<String, String, CompletableFuture<Boolean>> data = questions.get(e.getPlayer().getUniqueId());

            if (e.getMessage().equals(data.getFirst()) || e.getMessage().equals(data.getSecond())) {
                e.setCancelled(true);

                data.getThird().complete(e.getMessage().equals(data.getFirst()));
            }
        }

        // calc volume every move (if it wasn't just rot)
        @EventHandler
        public void onMove(PlayerMoveEvent e) {
            if (e.getFrom().distanceSquared(e.getTo()) == 0) return;

            calculateVolume(e.getPlayer().getUniqueId());
        }

        // let the website know the player is back in
        @EventHandler
        public void onJoin(PlayerJoinEvent e) {
            UUID uuid = e.getPlayer().getUniqueId();
            String id = players.get(uuid);

            calculateVolume(uuid);
            if (id != null) {
                webSockets.get(id).channel.writeAndFlush(new WebSocketPacket.JoinMinecraft());
            }
        }

        // player left; exit voice chat; let website know
        @EventHandler
        public void onQuit(PlayerQuitEvent e) {
            String id = players.get(e.getPlayer().getUniqueId());

            if (id != null) {
                removeAllPeers(id);
                webSockets.get(id).channel.writeAndFlush(new WebSocketPacket.LeaveMinecraft());
            }
        }
    }
}
