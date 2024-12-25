package io.github.maliciousfiles.serversideProxyChat.webSocket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import java.nio.charset.Charset;
import java.util.List;

public class WebSocketCodec extends CombinedChannelDuplexHandler<WebSocketCodec.VoiceServerPacketDecoder, WebSocketCodec.VoiceServerPacketEncoder> {

    public WebSocketCodec() {
        init(new VoiceServerPacketDecoder(), new VoiceServerPacketEncoder());
    }

    public static class VoiceServerPacketEncoder extends MessageToMessageEncoder<WebSocketPacket> {
        @Override
        protected void encode(ChannelHandlerContext ctx, WebSocketPacket msg, List<Object> out) {
            JsonObject in = new Gson().toJsonTree(msg).getAsJsonObject();
            in.addProperty("event", WebSocketPacket.CLASS_MAP.inverse().get(msg.getClass()));

            out.add(new TextWebSocketFrame(in.toString()));
        }
    }

    public static class VoiceServerPacketDecoder extends MessageToMessageDecoder<WebSocketFrame> {
        @Override
        protected void decode(ChannelHandlerContext ctx, WebSocketFrame msg, List<Object> out) {
            String in = msg instanceof TextWebSocketFrame text ? text.text() : msg instanceof BinaryWebSocketFrame bin ? bin.content().toString(Charset.defaultCharset()) : "";

            WebSocketPacket packet = new Gson().fromJson(in, WebSocketPacket.CLASS_MAP.get(JsonParser.parseString(in).getAsJsonObject().get("event").getAsString()));

            out.add(packet);
        }
    }
}
