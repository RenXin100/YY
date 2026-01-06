package com.renxin.client.state;

import com.renxin.common.network.NetworkConstants;
import net.minecraft.client.MinecraftClient;

import java.util.*;

public class ClientChannelManager {
    private static final ClientChannelManager INSTANCE = new ClientChannelManager();
    public static ClientChannelManager getInstance() { return INSTANCE; }

    // 存储所有可用频道
    private Set<String> channels = new HashSet<>();
    // 存储每个玩家所在的频道 (UUID -> ChannelName)
    private Map<UUID, String> playerChannelMap = new HashMap<>();

    // 默认只显示公共频道
    private ClientChannelManager() {
        channels.add(NetworkConstants.CHANNEL_PUBLIC);
    }

    // --- 数据更新 (由网络包触发) ---
    public void syncData(Set<String> newChannels, Map<UUID, String> newStates) {
        this.channels = newChannels;
        this.playerChannelMap = newStates;
    }

    // --- 查询方法 ---
    public Set<String> getChannels() {
        return channels;
    }

    public String getPlayerChannel(UUID uuid) {
        return playerChannelMap.getOrDefault(uuid, NetworkConstants.CHANNEL_PUBLIC);
    }

    public String getCurrentChannel() {
        if (MinecraftClient.getInstance().player == null) return NetworkConstants.CHANNEL_PUBLIC;
        return getPlayerChannel(MinecraftClient.getInstance().player.getUuid());
    }
}