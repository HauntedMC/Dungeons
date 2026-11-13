package nl.hauntedmc.dungeons.acceptance;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Native Paper player and packet sink; only the remote Minecraft client is replaced. */
final class NativeHudPlayer implements AutoCloseable {
    final Player player;
    private final Object handle;
    private final Object playerList;
    private final Object channel;
    private String actionBar = "";

    @SuppressWarnings("unchecked")
    NativeHudPlayer(UUID id) throws Exception {
        player = NativePlayerRecovery.nativePlayer(id);
        handle = player.getClass().getMethod("getHandle").invoke(player);
        Object server =
                Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        playerList = server.getClass().getMethod("getPlayerList").invoke(server);
        Class<?> flow = Class.forName("net.minecraft.network.protocol.PacketFlow");
        Object connection =
                Class.forName("net.minecraft.network.Connection")
                        .getConstructor(flow)
                        .newInstance(flow.getField("SERVERBOUND").get(null));
        Class<?> handler = Class.forName("io.netty.channel.ChannelHandler");
        Object emptyHandlers = java.lang.reflect.Array.newInstance(handler, 0);
        channel =
                Class.forName("io.netty.channel.embedded.EmbeddedChannel")
                        .getConstructor(emptyHandlers.getClass())
                        .newInstance(emptyHandlers);
        connection.getClass().getField("channel").set(connection, channel);
        connection
                .getClass()
                .getField("address")
                .set(connection, new InetSocketAddress("127.0.0.1", 25565));
        Object profile = handle.getClass().getMethod("getGameProfile").invoke(handle);
        Class<?> cookieType = Class.forName("net.minecraft.server.network.CommonListenerCookie");
        Object cookie =
                cookieType
                        .getMethod(
                                "createInitial",
                                Class.forName("com.mojang.authlib.GameProfile"),
                                boolean.class)
                        .invoke(null, profile, false);
        Object listener =
                Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl")
                        .getConstructor(
                                Class.forName("net.minecraft.server.MinecraftServer"),
                                connection.getClass(),
                                handle.getClass(),
                                cookieType)
                        .newInstance(server, connection, handle, cookie);
        handle.getClass().getField("connection").set(handle, listener);
        ((Map<UUID, Object>) field("playersByUUID")).put(id, handle);
        ((Map<String, Object>) field("playersByName"))
                .put(player.getName().toLowerCase(java.util.Locale.ROOT), handle);
        ((List<Object>) field("players")).add(handle);
        // Native admission normally sets this flag; Paper rejects recovery teleports without it.
        handle.getClass().getField("valid").setBoolean(handle, true);
        AcceptancePlugin.require(player.isOnline(), "Native HUD probe is not online");
    }

    String actionBar() {
        try {
            channel.getClass().getMethod("runPendingTasks").invoke(channel);
            Object packet;
            while ((packet = channel.getClass().getMethod("readOutbound").invoke(channel))
                    != null) {
                if (packet.getClass().getSimpleName().equals("ClientboundSetActionBarTextPacket")) {
                    Object text = packet.getClass().getMethod("text").invoke(packet);
                    actionBar =
                            (String)
                                    Class.forName("net.minecraft.network.chat.Component")
                                            .getMethod("getString")
                                            .invoke(text);
                }
            }
            return actionBar;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private Object field(String name) throws Exception {
        var field = Class.forName("net.minecraft.server.players.PlayerList").getDeclaredField(name);
        field.setAccessible(true);
        return field.get(playerList);
    }

    @Override
    public void close() {
        try {
            ((Map<?, ?>) field("playersByUUID")).remove(player.getUniqueId());
            ((Map<?, ?>) field("playersByName"))
                    .remove(player.getName().toLowerCase(java.util.Locale.ROOT));
            ((List<?>) field("players")).remove(handle);
            handle.getClass().getField("valid").setBoolean(handle, false);
            channel.getClass().getMethod("finishAndReleaseAll").invoke(channel);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
