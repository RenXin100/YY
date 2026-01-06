package com.renxin.client.audio;

import com.renxin.common.network.NetworkConstants;
import com.renxin.common.network.VoicePacket;
import net.minecraft.client.MinecraftClient;

import javax.sound.sampled.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpeakerManager {

    private boolean running = false;
    private OpusManager opusManager;
    private final Map<UUID, UserAudioStream> userStreams = new ConcurrentHashMap<>();

    private long packetCount = 0;

    public SpeakerManager() {
        this.opusManager = new OpusManager();
    }

    public void start() { running = true; }

    public void stop() {
        running = false;
        userStreams.values().forEach(UserAudioStream::close);
        userStreams.clear();
        if (opusManager != null) opusManager.close();
    }

    public void processPacket(VoicePacket packet) {
        if (!running) return;
        if (packet.getData() == null || packet.getData().length == 0) return;

        UUID senderUuid = packet.getPlayerUuid();

        // Echo cancellation (ignore self)
        if (MinecraftClient.getInstance().player != null &&
                senderUuid.equals(MinecraftClient.getInstance().player.getUuid())) {
            return;
        }

        UserAudioStream stream = userStreams.computeIfAbsent(senderUuid, uuid -> new UserAudioStream());

        try {
            short[] pcmData = opusManager.decode(packet.getData());

            packetCount++;
            if (packetCount % 50 == 0) {
                System.out.println("[RenVoice-Debug] 🔊 Processing Voice Packet: From " + senderUuid.toString().substring(0,5) +
                        " | Raw Size: " + packet.getData().length +
                        " | Decoded Samples: " + pcmData.length);
            }

            if (pcmData.length > 0) {
                stream.write(pcmData);
            } else {
                if (packetCount % 50 == 0) System.err.println("[RenVoice-Error] Decoded length is 0! Opus might be failing.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class UserAudioStream {
        private SourceDataLine line;

        public UserAudioStream() {
            try {
                AudioFormat format = new AudioFormat(NetworkConstants.SAMPLE_RATE, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

                if (AudioSystem.isLineSupported(info)) {
                    line = (SourceDataLine) AudioSystem.getLine(info);
                    line.open(format);
                    line.start();
                    System.out.println("[RenVoice-Debug] 🎧 Created Audio Stream (OpenAL/JavaSound) for new player");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void write(short[] pcmShorts) {
            if (line == null) return;
            byte[] pcmBytes = new byte[pcmShorts.length * 2];
            for (int i = 0; i < pcmShorts.length; i++) {
                pcmBytes[i*2] = (byte) (pcmShorts[i] & 0xff);
                pcmBytes[i*2+1] = (byte) ((pcmShorts[i] >> 8) & 0xff);
            }
            line.write(pcmBytes, 0, pcmBytes.length);
        }

        public void close() {
            if (line != null) { line.drain(); line.close(); }
        }
    }
}