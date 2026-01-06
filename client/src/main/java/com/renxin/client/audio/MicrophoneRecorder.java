package com.renxin.client.audio;

import com.renxin.client.config.VoiceSettings;
import com.renxin.client.network.UDPClient;
import com.renxin.common.network.NetworkConstants;
import com.renxin.common.network.VoicePacket;
import net.minecraft.client.MinecraftClient;

import javax.sound.sampled.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

public class MicrophoneRecorder implements Runnable {

    private UDPClient udpClient;
    private TargetDataLine microphone;
    private volatile boolean running;
    private OpusManager opusManager;
    private Thread workerThread;

    // 持有扬声器引用，用于把收到的包传给它
    private SpeakerManager speakerManager;

    // [修复] 无参构造函数 (解决 "实际为2个" 的报错)
    public MicrophoneRecorder() {
        this.opusManager = new OpusManager();
    }

    // [修复] 启动方法：接收 IP, 端口 和 扬声器管理器
    public void startRecording(String targetIp, int targetPort, SpeakerManager speakerManager) {
        this.speakerManager = speakerManager;

        // 防止重复启动
        stopRecording();

        try {
            // 1. 初始化 UDP 连接
            udpClient = new UDPClient();
            udpClient.connect(targetIp, targetPort);

            // 2. 启动 UDP 监听 (收到包 -> 交给 SpeakerManager)
            udpClient.startListening(packet -> {
                if (this.speakerManager != null) {
                    this.speakerManager.processPacket(packet);
                }
            });

            // 3. 发送握手包 (打通樱花映射的关键)
            if (MinecraftClient.getInstance().player != null) {
                UUID myUuid = MinecraftClient.getInstance().player.getUuid();
                udpClient.sendHandshake(myUuid);
            }

            // 4. 启动录音线程
            running = true;
            workerThread = new Thread(this, "RenVoice-Mic-Thread");
            workerThread.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        AudioFormat format = new AudioFormat(NetworkConstants.SAMPLE_RATE, 16, 1, true, false);

        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) return;

            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(format);
            microphone.start();

            byte[] pcmBuffer = new byte[NetworkConstants.FRAME_SIZE * 2];
            short[] shortBuffer = new short[NetworkConstants.FRAME_SIZE];
            long sequence = 0;
            UUID playerUuid = MinecraftClient.getInstance().player.getUuid();

            while (running && microphone.isOpen()) {
                int bytesRead = microphone.read(pcmBuffer, 0, pcmBuffer.length);

                if (bytesRead > 0) {
                    // 调试日志
                    // long volume = 0; for(short s : shortBuffer) volume+=Math.abs(s);
                    // if(volume/bytesRead > 10) System.out.println("Mic Input...");

                    if (VoiceSettings.getInstance().isMuted()) continue;
                    if (!VoiceSettings.getInstance().shouldSendVoice()) continue;

                    ByteBuffer.wrap(pcmBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortBuffer);
                    byte[] encodedData = opusManager.encode(shortBuffer);

                    if (encodedData != null && encodedData.length > 0) {
                        VoicePacket packet = new VoicePacket(
                                NetworkConstants.PACKET_VOICE,
                                playerUuid,
                                sequence++,
                                encodedData
                        );
                        udpClient.send(packet);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    // [修复] 停止方法 (解决 "无法解析 stop" 报错)
    public void stopRecording() {
        running = false;
        cleanup();
    }

    private void cleanup() {
        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }
        if (udpClient != null) {
            udpClient.close();
        }
    }
}