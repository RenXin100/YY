package com.renxin.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ServerRouteConfig {
    private static final ServerRouteConfig INSTANCE = new ServerRouteConfig();
    public static ServerRouteConfig getInstance() { return INSTANCE; }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("renvoice_routes.json");

    private RouteConfigData data = new RouteConfigData();

    public void load() {
        if (!Files.exists(CONFIG_FILE)) {
            // 默认生成一个示例配置
            data.routes.add(new RouteEntry("bj.sakura.com", "bj-voice.sakura.com", 20001, "北京线路"));
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
            data = GSON.fromJson(reader, RouteConfigData.class);
            if (data == null) data = new RouteConfigData();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 核心逻辑：根据玩家连接的域名，寻找匹配的语音配置
     */
    public RouteEntry matchRoute(String playerJoinedHost) {
        if (playerJoinedHost == null) return data.defaultRoute;

        // 遍历所有规则，看域名是否包含关键词 (或者完全匹配)
        for (RouteEntry route : data.routes) {
            if (playerJoinedHost.contains(route.matchHost)) {
                return route;
            }
        }
        return data.defaultRoute;
    }

    // --- 数据结构类 ---

    public static class RouteConfigData {
        // 默认线路 (保底)
        public RouteEntry defaultRoute = new RouteEntry("default", "127.0.0.1", 24454, "默认线路");
        // 特殊线路列表
        public List<RouteEntry> routes = new ArrayList<>();
    }

    public static class RouteEntry {
        public String matchHost;  // 玩家填写的地址 (如 bj.sakura)
        public String voiceIp;    // 实际语音IP
        public int voicePort;     // 实际语音UDP端口
        public String lineName;   // 显示在UI上的名字

        public RouteEntry(String match, String ip, int port, String name) {
            this.matchHost = match;
            this.voiceIp = ip;
            this.voicePort = port;
            this.lineName = name;
        }
    }
}