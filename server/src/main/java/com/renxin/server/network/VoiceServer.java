package com.renxin.server.network;

import com.renxin.common.network.NetworkConstants;
import com.renxin.common.network.VoicePacket;
import com.renxin.server.ServerChannelManager;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VoiceServer extends Thread {

    private static final Logger LOGGER = LoggerFactory.getLogger("RenVoice-Server");
    private final MinecraftServer mcServer;
    private DatagramSocket socket;
    private boolean running;
    private final Map<UUID, PlayerAddress> playerAddresses = new ConcurrentHashMap<>();

    // Debug Counter
    private long packetCounter = 0;

    public VoiceServer(MinecraftServer mcServer) {
        this.mcServer = mcServer;
        this.setName("RenVoice-UDP-Thread");
    }

    @Override
    public void run() {
        LOGGER.info("[RenVoice] Starting Voice UDP Server on port: " + NetworkConstants.DEFAULT_VOICE_PORT);
        try {
            socket = new DatagramSocket(NetworkConstants.DEFAULT_VOICE_PORT);
            running = true;
            byte[] buffer = new byte[4096];

            while (running && !socket.isClosed()) {
                try {
                    DatagramPacket rawPacket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(rawPacket);
                    handlePacket(rawPacket);
                } catch (IOException e) {
                    if (running) LOGGER.error("UDP receive error", e);
                }
            }
        } catch (SocketException e) {
            LOGGER.error("Failed to bind UDP port!", e);
        } finally {
            close();
        }
    }

    private void handlePacket(DatagramPacket rawPacket) {
        byte[] validBytes = new byte[rawPacket.getLength()];
        System.arraycopy(rawPacket.getData(), 0, validBytes, 0, rawPacket.getLength());
        VoicePacket packet = VoicePacket.fromBytes(validBytes);

        if (packet == null) return;

        UUID senderUuid = packet.getPlayerUuid();
        playerAddresses.put(senderUuid, new PlayerAddress(rawPacket.getAddress(), rawPacket.getPort()));

        if (packet.getType() == NetworkConstants.PACKET_VOICE) {
            forwardVoicePacket(senderUuid, rawPacket);
        } else if (packet.getType() == NetworkConstants.PACKET_HANDSHAKE) {
            LOGGER.info("[RenVoice-Debug] Handshake received -> " + senderUuid + " Addr: " + rawPacket.getAddress() + ":" + rawPacket.getPort());
        }
    }

    private void forwardVoicePacket(UUID senderUuid, DatagramPacket originPacket) {
        packetCounter++;
        boolean logThisTime = (packetCounter % 50 == 0); // Log every ~1 second

        String channelName = ServerChannelManager.getInstance().getPlayerChannel(senderUuid);

        if (channelName == null) {
            if (logThisTime) System.out.println("[RenVoice-Debug] ❌ DROP: Player " + senderUuid.toString().substring(0,5) + "... not in any channel");
            return;
        }

        Set<UUID> members = ServerChannelManager.getInstance().getChannelMembers(channelName);
        if (members == null || members.isEmpty()) return;

        for (UUID memberUuid : members) {
            if (memberUuid.equals(senderUuid)) continue;

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

                    if (logThisTime) {
                        System.out.println("   -> Forwarding to: " + memberUuid.toString().substring(0,5) + "... @ " + target.ip + ":" + target.port);
                    }
                } catch (IOException e) {
                    LOGGER.error("Forward failed", e);
                }
            } else {
                if (logThisTime) System.out.println("   -> ⚠️ Cannot forward to " + memberUuid.toString().substring(0,5) + ": Unknown UDP address (No handshake?)");
            }
        }
    }

    public void close() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
    }

    private static class PlayerAddress {
        InetAddress ip;
        int port;
        public PlayerAddress(InetAddress ip, int port) { this.ip = ip; this.port = port; }
    }
}