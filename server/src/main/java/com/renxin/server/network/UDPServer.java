package com.renxin.server.network;

import com.renxin.common.network.NetworkConstants;
import com.renxin.common.network.VoicePacket;
import com.renxin.server.ServerChannelManager;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UDPServer extends Thread {

    private DatagramSocket socket;
    private boolean running;

    // 存储玩家的 UDP 地址 (UUID -> IP:Port)
    private final Map<UUID, PlayerAddress> playerAddresses = new ConcurrentHashMap<>();

    public UDPServer() {
        try {
            this.socket = new DatagramSocket(NetworkConstants.DEFAULT_VOICE_PORT);
            this.running = true;
            System.out.println("[RenVoice] UDP 服务器启动 -> 端口: " + NetworkConstants.DEFAULT_VOICE_PORT);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopServer() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    @Override
    public void run() {
        byte[] buffer = new byte[4096];

        while (running && !socket.isClosed()) {
            try {
                DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                socket.receive(dp);
                System.out.println("[UDP-RAW] 收到来自 " + dp.getAddress() + ":" + dp.getPort() + " 的数据包，长度: " + dp.getLength());
                byte[] data = new byte[dp.getLength()];
                System.arraycopy(dp.getData(), 0, data, 0, dp.getLength());
                VoicePacket packet = VoicePacket.fromBytes(data);

                // [重点修复] 必须使用 getPlayerUuid()，因为 VoicePacket 里定义的 getter 是这个
                // 如果你的 IDE 这里还报错，请务必检查 common 模块的 VoicePacket.java
                UUID senderUuid = packet.getPlayerUuid();

                // 更新地址映射
                playerAddresses.put(senderUuid, new PlayerAddress(dp.getAddress(), dp.getPort()));

                // 握手包处理
                if (packet.getType() == NetworkConstants.PACKET_HANDSHAKE) {
                    continue;
                }

                // 语音包转发
                if (packet.getType() == NetworkConstants.PACKET_VOICE) {
                    forwardVoicePacket(senderUuid, dp);
                }

            } catch (Exception e) {
                if (running) e.printStackTrace();
            }
        }
    }

    private void forwardVoicePacket(UUID senderUuid, DatagramPacket originPacket) {
        // 1. 获取玩家所在频道 (现在 ServerChannelManager 有这个方法了)
        String channelName = ServerChannelManager.getInstance().getPlayerChannel(senderUuid);
        if (channelName == null) return;

        // 2. 获取同频道成员 (现在 ServerChannelManager 也有这个方法了)
        Set<UUID> members = ServerChannelManager.getInstance().getChannelMembers(channelName);
        if (members == null || members.isEmpty()) return;

        // 3. 转发
        for (UUID memberUuid : members) {
            if (memberUuid.equals(senderUuid)) continue; // 不发给自己

            PlayerAddress target = playerAddresses.get(memberUuid);
            if (target != null) {
                try {
                    DatagramPacket forwardPacket = new DatagramPacket(
                            originPacket.getData(),
                            originPacket.getLength(),
                            target.ip,
                            target.port
                    );
                    socket.send(forwardPacket);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static class PlayerAddress {
        InetAddress ip;
        int port;
        public PlayerAddress(InetAddress ip, int port) {
            this.ip = ip;
            this.port = port;
        }
    }
}