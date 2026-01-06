package com.renxin.client.network;

import com.renxin.RenVoiceClient;
import com.renxin.client.config.VoiceSettings;
import com.renxin.client.state.ClientChannelManager;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;

import java.util.*;

public class VoiceClientNetwork {

    public static final Identifier CHANNEL_SYNC_ID = new Identifier("renvoice", "channel_sync");
    public static final Identifier JOIN_CHANNEL_ID = new Identifier("renvoice", "join_channel");
    public static final Identifier MUTE_SYNC_ID = new Identifier("renvoice", "mute_sync");
    public static final Identifier ROUTE_SYNC_ID = new Identifier("renvoice", "route_sync");

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CHANNEL_SYNC_ID, (client, handler, buf, responseSender) -> {
            int cCount = buf.readInt();
            Set<String> channels = new HashSet<>();
            for(int i=0; i<cCount; i++) channels.add(buf.readString());

            int sCount = buf.readInt();
            Map<UUID, String> states = new HashMap<>();
            for(int i=0; i<sCount; i++) states.put(buf.readUuid(), buf.readString());

            client.execute(() -> ClientChannelManager.getInstance().syncData(channels, states));
        });

        ClientPlayNetworking.registerGlobalReceiver(MUTE_SYNC_ID, (client, handler, buf, responseSender) -> {
            long endTime = buf.readLong();
            client.execute(() -> VoiceSettings.getInstance().setMuteEndTime(endTime));
        });

        // [新增] 监听路由配置
        ClientPlayNetworking.registerGlobalReceiver(ROUTE_SYNC_ID, (client, handler, buf, responseSender) -> {
            String newIp = buf.readString();
            int newPort = buf.readInt();
            String lineName = buf.readString();

            client.execute(() -> {
                // 如果 IP 或 端口 变了，才重启
                if (!newIp.equals(RenVoiceClient.TARGET_IP) || newPort != RenVoiceClient.TARGET_PORT) {

                    RenVoiceClient.TARGET_IP = newIp;
                    RenVoiceClient.TARGET_PORT = newPort;

                    if (RenVoiceClient.INSTANCE != null) {
                        RenVoiceClient.INSTANCE.restartVoiceSystem();
                        if (client.player != null) {
                            client.player.sendMessage(Text.of("§b[RenVoice] 已自动切换至线路: " + lineName), false);
                        }
                    }
                }
            });
        });
    }

    public static void sendJoinRequest(String channelName) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(channelName);
        ClientPlayNetworking.send(JOIN_CHANNEL_ID, buf);
    }
}