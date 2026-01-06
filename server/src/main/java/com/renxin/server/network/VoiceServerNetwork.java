package com.renxin.server.network;

import com.renxin.common.access.IVirtualHostHolder;
import com.renxin.server.ServerChannelManager;
import com.renxin.server.ServerRouteConfig;
import com.renxin.server.mixin.ServerPlayNetworkHandlerAccessor;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;

public class VoiceServerNetwork {

    public static final Identifier JOIN_CHANNEL_ID = new Identifier("renvoice", "join_channel");
    public static final Identifier CHANNEL_SYNC_ID = new Identifier("renvoice", "channel_sync");
    public static final Identifier MUTE_SYNC_ID = new Identifier("renvoice", "mute_sync");
    public static final Identifier ROUTE_SYNC_ID = new Identifier("renvoice", "route_sync");

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(JOIN_CHANNEL_ID, (server, player, handler, buf, responseSender) -> {
            String targetChannel = buf.readString();
            server.execute(() -> {
                ServerChannelManager.getInstance().joinChannel(player, targetChannel);
                broadcastSync(server);
            });
        });
    }

    public static void sendRouteConfig(ServerPlayerEntity player) {
        String hostname = "default";
        // [修复] 直接访问 .connection 字段
        ServerPlayNetworkHandlerAccessor accessor = (ServerPlayNetworkHandlerAccessor) player.networkHandler;
        ClientConnection connection = accessor.renvoice$getConnection();

        if (connection instanceof IVirtualHostHolder holder) {
            String captured = holder.renvoice$getVirtualHost();
            if (captured != null) hostname = captured;
        }

        ServerRouteConfig.RouteEntry route = ServerRouteConfig.getInstance().matchRoute(hostname);

        System.out.println("[RenVoice] 玩家 " + player.getName().getString() + " (线路:" + hostname + ") -> 分配 UDP: " + route.voicePort);

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(route.voiceIp);
        buf.writeInt(route.voicePort);
        buf.writeString(route.lineName);

        ServerPlayNetworking.send(player, ROUTE_SYNC_ID, buf);
    }

    public static void broadcastSync(MinecraftServer server) {
        ServerChannelManager.SyncData data = ServerChannelManager.getInstance().getSyncData();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(data.channels.size());
        for (String channel : data.channels) buf.writeString(channel);
        buf.writeInt(data.states.size());
        for (Map.Entry<UUID, String> entry : data.states.entrySet()) {
            buf.writeUuid(entry.getKey());
            buf.writeString(entry.getValue());
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, CHANNEL_SYNC_ID, buf);
        }
    }

    public static void sendMuteUpdate(ServerPlayerEntity player, long endTime) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeLong(endTime);
        ServerPlayNetworking.send(player, MUTE_SYNC_ID, buf);
    }
}