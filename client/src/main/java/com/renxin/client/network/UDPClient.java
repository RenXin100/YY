package com.renxin.client.network;

import com.renxin.common.network.VoicePacket;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.UUID;
import java.util.function.Consumer;

public class UDPClient {
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;
    private boolean listening = false;
    private Thread listenThread;
    private Consumer<VoicePacket> onPacketReceived;

    private long receiveCounter = 0;

    public UDPClient() throws Exception {
        this.socket = new DatagramSocket();
    }

    public void connect(String ip, int port) throws Exception {
        this.serverAddress = InetAddress.getByName(ip);
        this.serverPort = port;
        System.out.println("[RenVoice-Debug] UDP Client connected to -> " + ip + ":" + port);
    }

    public void send(VoicePacket packet) {
        try {
            byte[] data = packet.toBytes();
            DatagramPacket dp = new DatagramPacket(data, data.length, serverAddress, serverPort);
            socket.send(dp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendHandshake(UUID uuid) {
        VoicePacket packet = new VoicePacket(0, uuid, 0, new byte[0]);
        send(packet);
        System.out.println("[RenVoice-Debug] Handshake sent -> UDP channel request");
    }

    public void startListening(Consumer<VoicePacket> callback) {
        this.onPacketReceived = callback;
        this.listening = true;

        listenThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            System.out.println("[RenVoice-Debug] UDP Listener thread started...");

            while (listening && !socket.isClosed()) {
                try {
                    DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                    socket.receive(dp);

                    receiveCounter++;
                    if (receiveCounter % 50 == 0) {
                        System.out.println("[RenVoice-Debug] 📥 UDP Packet Received! Size: " + dp.getLength() + " | From: " + dp.getAddress() + ":" + dp.getPort());
                    }

                    byte[] data = new byte[dp.getLength()];
                    System.arraycopy(dp.getData(), 0, data, 0, dp.getLength());
                    VoicePacket packet = VoicePacket.fromBytes(data);

                    if (onPacketReceived != null) {
                        onPacketReceived.accept(packet);
                    }
                } catch (Exception e) {
                    if (listening) e.printStackTrace();
                }
            }
        }, "UDP-Listener");
        listenThread.start();
    }

    public void close() {
        listening = false;
        if (socket != null && !socket.isClosed()) socket.close();
    }
}