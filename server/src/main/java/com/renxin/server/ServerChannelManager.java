package com.renxin.server;

import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ServerChannelManager {
    private static final ServerChannelManager INSTANCE = new ServerChannelManager();
    public static ServerChannelManager getInstance() { return INSTANCE; }

    // 频道名 -> 成员列表 (用于转发语音)
    private final Map<String, Set<UUID>> channelMembers = new ConcurrentHashMap<>();

    // 玩家 -> 频道名 (用于快速查找玩家在哪)
    private final Map<UUID, String> playerChannels = new ConcurrentHashMap<>();

    // --- [新增/修复] 给 UDPServer 调用的核心方法 ---

    /**
     * 获取某个频道的所有成员 UUID
     */
    public Set<UUID> getChannelMembers(String channelName) {
        return channelMembers.getOrDefault(channelName, Collections.emptySet());
    }

    /**
     * 获取某个玩家当前所在的频道名
     */
    public String getPlayerChannel(UUID playerUuid) {
        return playerChannels.get(playerUuid);
    }

    // ---------------------------------------------

    public boolean createChannel(String name, ServerPlayerEntity creator) {
        if (channelMembers.containsKey(name)) return false;
        channelMembers.put(name, ConcurrentHashMap.newKeySet());
        // 创建者自动加入
        joinChannel(creator, name);
        return true;
    }

    public boolean removeChannel(String name) {
        if (!channelMembers.containsKey(name)) return false;
        // 踢出所有成员
        Set<UUID> members = channelMembers.get(name);
        for (UUID uuid : members) {
            playerChannels.remove(uuid);
        }
        channelMembers.remove(name);
        return true;
    }

    public void joinChannel(ServerPlayerEntity player, String channelName) {
        if (!channelMembers.containsKey(channelName)) return;

        // 先退出旧频道
        String oldChannel = playerChannels.get(player.getUuid());
        if (oldChannel != null) {
            Set<UUID> oldMembers = channelMembers.get(oldChannel);
            if (oldMembers != null) oldMembers.remove(player.getUuid());
        }

        // 加入新频道
        playerChannels.put(player.getUuid(), channelName);
        channelMembers.get(channelName).add(player.getUuid());
    }

    public boolean invitePlayer(String channelName, UUID targetUuid) {
        // 简单实现：只是检查频道是否存在，实际上邀请逻辑通常是发消息给玩家
        // 这里我们假设是强制拉人或者邀请逻辑在指令层处理
        return channelMembers.containsKey(channelName);
    }

    public void onPlayerDisconnect(UUID playerUuid) {
        String channel = playerChannels.remove(playerUuid);
        if (channel != null) {
            Set<UUID> members = channelMembers.get(channel);
            if (members != null) members.remove(playerUuid);
        }
    }

    // 用于网络同步的数据结构
    public SyncData getSyncData() {
        return new SyncData(channelMembers.keySet(), playerChannels);
    }

    public static class SyncData {
        public Set<String> channels;
        public Map<UUID, String> states;

        public SyncData(Set<String> c, Map<UUID, String> s) {
            this.channels = c;
            this.states = s;
        }
    }
}