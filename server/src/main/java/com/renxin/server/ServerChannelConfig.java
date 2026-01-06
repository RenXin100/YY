package com.renxin.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ServerChannelConfig {
    private static final ServerChannelConfig INSTANCE = new ServerChannelConfig();
    public static ServerChannelConfig getInstance() { return INSTANCE; }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("renvoice_channels.json");

    // 数据结构：频道名 -> 白名单玩家UUID列表
    private Map<String, Set<UUID>> channelWhitelists = new HashMap<>();
    private Map<String, Long> mutedPlayers = new HashMap<>(); // JSON Key 必须是 String, 所以用 UUID.toString()
    public void load() {
        if (!Files.exists(CONFIG_FILE)) {
            save(); // 如果不存在，先创建一个空的
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
            // 反序列化
            channelWhitelists = GSON.fromJson(reader, new TypeToken<Map<String, Set<UUID>>>(){}.getType());
            if (channelWhitelists == null) channelWhitelists = new HashMap<>();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(channelWhitelists, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- 业务逻辑 ---

    public boolean channelExists(String name) {
        return channelWhitelists.containsKey(name);
    }

    public void createChannel(String name) {
        if (!channelWhitelists.containsKey(name)) {
            channelWhitelists.put(name, new HashSet<>());
            save();
        }
    }

    public void removeChannel(String name) {
        channelWhitelists.remove(name);
        save();
    }

    public void addToWhitelist(String channel, UUID playerUuid) {
        Set<UUID> list = channelWhitelists.computeIfAbsent(channel, k -> new HashSet<>());
        list.add(playerUuid);
        save();
    }

    public boolean canJoin(String channel, UUID playerUuid) {
        if (!channelWhitelists.containsKey(channel)) return false;
        // 白名单包含该玩家，或者该频道列表是空的（可选：如果想做开放私有频道）
        // 这里设计为：创建即私有，必须邀请才能进
        return channelWhitelists.get(channel).contains(playerUuid);
    }

    // 获取所有频道名用于同步
    public Set<String> getAllChannelNames() {
        return new HashSet<>(channelWhitelists.keySet());
    }
    // --- [新增] 禁言相关方法 ---
    public void mutePlayer(UUID uuid, int minutes) {
        long endTime = System.currentTimeMillis() + (minutes * 60L * 1000L);
        mutedPlayers.put(uuid.toString(), endTime);
        save();
    }

    public void unmutePlayer(UUID uuid) {
        mutedPlayers.remove(uuid.toString());
        save();
    }

    public boolean isMuted(UUID uuid) {
        String key = uuid.toString();
        if (!mutedPlayers.containsKey(key)) return false;

        long endTime = mutedPlayers.get(key);
        if (System.currentTimeMillis() > endTime) {
            // 已过期，自动清理
            mutedPlayers.remove(key);
            save(); // 懒更新
            return false;
        }
        return true;
    }

    public long getMuteEndTime(UUID uuid) {
        return mutedPlayers.getOrDefault(uuid.toString(), 0L);
    }
}