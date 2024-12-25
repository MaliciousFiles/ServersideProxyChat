package io.github.maliciousfiles.serversideProxyChat.websocket;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public interface WebSocketPacket {
    BiMap<String, Class<? extends WebSocketPacket>> CLASS_MAP = ImmutableBiMap.<String, Class<? extends WebSocketPacket>>builder()
            // INBOUND
            .put("validate", Validate.class)
            .put("join", Join.class)
            .put("relay_ice_candidate", RelayICECandidate.class)
            .put("relay_session_description", RelaySessionDescription.class)
            .put("request_player_list", RequestPlayerList.class)
            .put("volume_visualization", VolumeVisualization.class)
            .put("close", Close.class)
            // OUTBOUND
            .put("remove_peer", RemovePeer.class)
            .put("session_description", SessionDescription.class)
            .put("ice_candidate", ICECandidate.class)
            .put("join_minecraft", JoinMinecraft.class)
            .put("leave_minecraft", LeaveMinecraft.class)
            .put("force_mute", ForceMute.class)
            .put("join_verify", JoinVerify.class)
            .put("add_peer", AddPeer.class)
            .put("set_volume", SetVolume.class)
            .put("player_list", PlayerList.class)
            .put("disconnected", Disconnected.class)
            // INOUT
            .put("mute_self", MuteSelf.class)
            .build();

    // UTIL
    record ICECandidateObject(int sdpMLineIndex, String candidate) {}
    record SessionDescriptionObject(String sdp, String type) {}

    // INBOUND
    record Close() implements WebSocketPacket {}
    record Validate (String id) implements WebSocketPacket {}
    record Join (String uuid) implements WebSocketPacket {}
    record RelayICECandidate(String peer_id, ICECandidateObject ice_candidate) implements WebSocketPacket {}
    record RelaySessionDescription(String peer_id, SessionDescriptionObject session_description) implements WebSocketPacket {}
    record RequestPlayerList() implements WebSocketPacket {}
    record VolumeVisualization(String actionBar) implements WebSocketPacket {}

    // OUTBOUND
    record RemovePeer(String peer_id) implements WebSocketPacket {}
    record SessionDescription(String peer_id, SessionDescriptionObject session_description) implements WebSocketPacket {}
    record ICECandidate(String peer_id, ICECandidateObject ice_candidate) implements WebSocketPacket {}
    record JoinMinecraft() implements WebSocketPacket {}
    record LeaveMinecraft() implements WebSocketPacket {}
    record ForceMute(boolean muted) implements WebSocketPacket {}
    record JoinVerify(boolean valid, String message) implements WebSocketPacket {

        public static JoinVerify of(VerifyResponse response, String playerName) {
            return new JoinVerify(response.verified, response.message.replace("{name}", playerName));
        }

        public enum VerifyResponse {
            ACCEPTED(true, "{name} successfully validated in Minecraft."),
            NOT_ACCEPTED(false, "{name} did not accept in Minecraft!"),
            NOT_FOUND(false, "Could not find Minecraft player with name {name}."),
            TIMED_OUT(false, "{name} did not respond within 1 minute."),
            TAKEN(false, "{name} is already connected. Contact admin if this is an error.");

            public final boolean verified;
            public final String message;

            VerifyResponse(boolean verified, String message) {
                this.verified = verified;
                this.message = message;
            }
        }
    }
    record AddPeer(String peer_id, boolean should_create_offer) implements WebSocketPacket {}
    class SetVolume implements WebSocketPacket {
        public final Map<String, Double> volume = new HashMap<>();

        public SetVolume() {}

        public SetVolume volume(String peerId, double volume) {
            this.volume.put(peerId, volume);

            return this;
        }
    }
    class PlayerList implements WebSocketPacket {
        public final Map<String, String> players = new HashMap<>();

        public PlayerList() {}

        public PlayerList players(Collection<? extends Player> list) {
            list.forEach((p) -> players.put(p.getName(), p.getUniqueId().toString()));

            return this;
        }
    }
    record Disconnected() implements WebSocketPacket {}

    // INOUT
    record MuteSelf(boolean muted) implements WebSocketPacket {}
}