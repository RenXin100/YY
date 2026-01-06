package com.renxin.common.network;

import net.minecraft.util.Identifier;

public class NetworkConstants {
    // 基础配置
    public static final int SAMPLE_RATE = 48000;
    public static final int FRAME_SIZE = 960;

    // --- [修复] 之前缺少的端口定义 ---
    public static final int DEFAULT_VOICE_PORT = 24454; // 语音服务UDP端口

    // --- 数据包类型定义 ---
    // [修复] 握手包：客户端告诉服务端 "我是谁(UUID)"，服务端记录 IP:Port
    public static final byte PACKET_HANDSHAKE = 0x00;

    // 语音包：包含 Opus 编码数据
    public static final byte PACKET_VOICE = 0x01;

    // 频道相关 (为接下来的功能做准备)
    public static final byte PACKET_JOIN_CHANNEL = 0x02;
    public static final byte PACKET_CHANNEL_SYNC = 0x03;

    // 默认频道ID
    public static final String CHANNEL_PUBLIC = "公共频道";
    // ...
    public static final byte PACKET_MUTE_STATE = 0x04; // 服务端 -> 客户端: 禁言状态更新
    public static final Identifier PACKET_CONFIG_ID = new Identifier("renvoice", "route_sync");
}