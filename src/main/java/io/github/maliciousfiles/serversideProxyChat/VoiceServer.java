package io.github.maliciousfiles.serversideProxyChat;

import io.github.maliciousfiles.serversideProxyChat.util.Pair;
import io.github.maliciousfiles.serversideProxyChat.websocket.WebSocketCodec;
import io.github.maliciousfiles.serversideProxyChat.websocket.WebSocketServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.OptionalSslHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConnectionListener;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class VoiceServer {
    public static String ip;

    public static void start() {
        new Thread(() -> {
            try {
                init();
            } catch (IOException | GeneralSecurityException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    private static Runnable removeListener;

    private static void init() throws IOException, GeneralSecurityException, URISyntaxException {
        ip = new BufferedReader(new InputStreamReader(new URI("http://checkip.amazonaws.com").toURL().openStream())).readLine();
        Pair<KeyStore, char[]> ks = generateKeystore(ip);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks.getFirst(), ks.getSecond());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks.getFirst());

        SslContext sslContext = SslContextBuilder.forServer(kmf)
                .trustManager(tmf)
                .clientAuth(ClientAuth.NONE)
                .build();

        MinecraftServer server = MinecraftServer.getServer();

        try {
            Field connection = MinecraftServer.class.getDeclaredField("connection");
            Field channels = ServerConnectionListener.class.getDeclaredField("channels");
            connection.setAccessible(true);
            channels.setAccessible(true);

            ((List<ChannelFuture>) channels.get(connection.get(server))).getFirst().addListener(f -> {
                if (f instanceof DefaultChannelPromise promise) {
                    promise.channel().pipeline().addFirst("ServersideProxyChatListener", new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            if (!(msg instanceof Channel channel)) return;

                            channel.pipeline().addFirst("register_http", new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
                                    super.channelRegistered(ctx); // let MC add all of its stuff

                                    ByteBuf[] lastMsg = {null};
                                    channel.pipeline().addFirst("save_http_msg", new ChannelInboundHandlerAdapter() {
                                        @Override
                                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                            if (msg instanceof ByteBuf buf) lastMsg[0] = buf.copy();
                                            super.channelRead(ctx, msg);
                                        }
                                    });
                                    channel.pipeline().addAfter("decoder", "detect_http", new ChannelInboundHandlerAdapter() {
                                        @Override
                                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                            if (!(cause instanceof DecoderException)) {
                                                super.exceptionCaught(ctx, cause);
                                                return;
                                            }

                                            // remove everything but the tail context
                                            for (int i = ctx.pipeline().names().size()-2; i >= 0; i--) {
                                                ctx.pipeline().remove(ctx.pipeline().names().get(i));
                                            }

                                            ctx.pipeline().addLast(
                                                    new OptionalSslHandler(sslContext) {
                                                        @Override
                                                        protected ChannelHandler newNonSslHandler(ChannelHandlerContext ctx) {
                                                            HttpResponseEncoder encoder = new HttpResponseEncoder();

                                                            try {
                                                                encoder.write(ctx, new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.MOVED_PERMANENTLY, DefaultHttpHeadersFactory.headersFactory().newHeaders().add("Location", "https://"+ip+":"+server.getPort())), ctx.voidPromise());
                                                                ctx.flush();
                                                                ctx.close();
                                                            } catch (Exception e) {
                                                                throw new RuntimeException(e);
                                                            }

                                                            return null;
                                                        }
                                                    },
                                                    new HttpServerCodec(),
                                                    new HttpObjectAggregator(65536),
                                                    new SimpleChannelInboundHandler<FullHttpRequest>(false) {
                                                        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws IOException {
                                                            switch(req.uri()) {
                                                                case "/" -> {
                                                                    ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(VoiceServer.class.getClassLoader().getResourceAsStream("client.html").readAllBytes())));
                                                                    ctx.close();
                                                                }
                                                                case "/websocket" -> ctx.fireChannelRead(req);
                                                                default -> {
                                                                    ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND));
                                                                    ctx.close();
                                                                }
                                                            }
                                                        }
                                                    },
                                                    new ChannelInboundHandlerAdapter() {
                                                        @Override
                                                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                                            if (msg instanceof CloseWebSocketFrame) {
                                                                super.channelRead(ctx, new TextWebSocketFrame("{'event': 'close'}"));
                                                            }

                                                            super.channelRead(ctx, msg);
                                                        }
                                                    },
                                                    new WebSocketServerProtocolHandler("/websocket", null, false, 65536),
                                                    new WebSocketFrameAggregator(65536),
                                                    new ChannelInboundHandlerAdapter() {
                                                        @Override
                                                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                                            if (!(cause instanceof DecoderException && (cause.getMessage().contains("unknown_ca") || cause.getMessage().contains("certificate_unknown")))) {
                                                                System.out.println(cause.getClass().getName()+": "+cause.getMessage());
                                                            }
                                                        }
                                                    },
                                                    new WebSocketCodec(),
                                                    new WebSocketServer()
                                            );

                                            ctx.pipeline().fireChannelRead(lastMsg[0]);
                                        }
                                    });
                                }
                            });

                            super.channelRead(ctx, msg); // let the bootstrapper set it up
                        }
                    });

                    removeListener = () -> promise.channel().pipeline().remove("ServersideProxyChatListener");
                }
            });
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

        WebSocketServer.initListener();
    }

    public static Pair<KeyStore, char[]> generateKeystore(String ip)
            throws GeneralSecurityException, IOException {
        File keystoreFile = Path.of(ServersideProxyChat.getInstance().getDataFolder().getAbsolutePath())
                .getParent().resolve("keystore.jks").toFile();

        char[] pass = SecureRandom.getInstanceStrong()
                .ints(16, 33, 127)
                .mapToObj(i -> (char) i)
                .reduce(new StringBuilder(), StringBuilder::append, StringBuilder::append).toString().toCharArray();

        final List<String> commandParameters = new ArrayList<>(List.of("keytool", "-genkey"));
        commandParameters.addAll(List.of("-alias", ip));
        commandParameters.addAll(List.of("-keyalg", "RSA"));
        commandParameters.addAll(List.of("-keysize", "2048"));
        commandParameters.addAll(List.of("-sigalg", "SHA256withRSA"));
        commandParameters.addAll(List.of("-storetype", "JKS"));
        commandParameters.addAll(List.of("-keystore", keystoreFile.getAbsolutePath()));
        commandParameters.addAll(List.of("-storepass", new String(pass)));
        commandParameters.addAll(List.of("-keypass", new String(pass)));
        commandParameters.addAll(List.of("-dname", "CN="+ip+", OU=ServersideProxyChat, O=ServersideProxyChat, L=Unknown, ST=Unknown, C=Unknown"));
        commandParameters.addAll(List.of("-validity", "36500"));
        commandParameters.addAll(List.of("-deststoretype", "pkcs12"));
        ProcessBuilder keytool = new ProcessBuilder().command(commandParameters);

        final Process process = keytool.start();
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            throw new IOException("Keytool execution error");
        }

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(new FileInputStream(keystoreFile), pass);

        keystoreFile.delete();
        return Pair.of(keyStore, pass);
    }

    public static void stop() {
        if (removeListener != null) removeListener.run();
        WebSocketServer.unload();
    }
}
