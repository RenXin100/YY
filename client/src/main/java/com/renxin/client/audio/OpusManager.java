package com.renxin.client.audio;

import com.renxin.common.network.NetworkConstants;
import com.sun.jna.ptr.PointerByReference;

// Core: Use the hybrid approach (tomp2p interface + club.minnced loader)
import tomp2p.opuswrapper.Opus;
import club.minnced.opus.util.OpusLibrary;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public class OpusManager {

    static {
        try {
            System.out.println("[RenVoice] Executing hybrid loading strategy...");

            // Auto-extract and load DLL
            OpusLibrary.loadFromJar();

            System.out.println("[RenVoice] Opus native library loaded successfully!");
        } catch (Throwable e) {
            e.printStackTrace();
            throw new ExceptionInInitializerError("Opus library load failed: " + e.getMessage());
        }
    }

    private PointerByReference encoder;
    private PointerByReference decoder;

    private final IntBuffer errorBuffer;
    private final ByteBuffer encodeOutputBuffer;
    private final ShortBuffer decodeOutputBuffer;

    public OpusManager() {
        errorBuffer = IntBuffer.allocate(1);
        encodeOutputBuffer = ByteBuffer.allocateDirect(4096);
        decodeOutputBuffer = ShortBuffer.allocate(NetworkConstants.FRAME_SIZE);

        initOpus();
    }

    private void initOpus() {
        encoder = Opus.INSTANCE.opus_encoder_create(
                NetworkConstants.SAMPLE_RATE,
                1,
                Opus.OPUS_APPLICATION_VOIP,
                errorBuffer
        );
        checkError("Encoder creation");

        decoder = Opus.INSTANCE.opus_decoder_create(
                NetworkConstants.SAMPLE_RATE,
                1,
                errorBuffer
        );
        checkError("Decoder creation");
    }

    public byte[] encode(short[] pcmData) {
        if (encoder == null) return new byte[0];

        encodeOutputBuffer.clear();
        ShortBuffer pcmBuffer = ShortBuffer.wrap(pcmData);

        int encodedLength = Opus.INSTANCE.opus_encode(
                encoder,
                pcmBuffer,
                NetworkConstants.FRAME_SIZE,
                encodeOutputBuffer,
                encodeOutputBuffer.capacity()
        );

        if (encodedLength < 0) {
            return new byte[0];
        }

        byte[] result = new byte[encodedLength];
        encodeOutputBuffer.get(result);
        return result;
    }

    public short[] decode(byte[] opusData) {
        if (decoder == null) return new short[0];

        decodeOutputBuffer.clear();

        int decodedLength = Opus.INSTANCE.opus_decode(
                decoder,
                opusData,
                (opusData == null ? 0 : opusData.length),
                decodeOutputBuffer,
                NetworkConstants.FRAME_SIZE,
                0
        );

        if (decodedLength < 0) {
            return new short[0];
        }

        short[] result = new short[decodedLength];
        decodeOutputBuffer.get(result);
        return result;
    }

    private void checkError(String tag) {
        int error = errorBuffer.get(0);
        if (error != Opus.OPUS_OK) {
            System.err.println("[RenVoice] " + tag + " failed, error code: " + error);
        }
    }

    public void close() {
        if (encoder != null) {
            Opus.INSTANCE.opus_encoder_destroy(encoder);
            encoder = null;
        }
        if (decoder != null) {
            Opus.INSTANCE.opus_decoder_destroy(decoder);
            decoder = null;
        }
    }
}