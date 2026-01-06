package com.renxin.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;

public class VoicePacket {
    private int type;
    private UUID playerUuid;
    private long sequence;
    private byte[] data;

    public VoicePacket(int type, UUID playerUuid, long sequence, byte[] data) {
        this.type = type;
        this.playerUuid = playerUuid;
        this.sequence = sequence;
        this.data = data;
    }

    // --- 这一段必须要有，否则 SpeakerManager 会报错 ---
    public int getType() { return type; }
    public UUID getPlayerUuid() { return playerUuid; } // <--- 就是它！
    public long getSequence() { return sequence; }
    public byte[] getData() { return data; }
    // ------------------------------------------------

    public byte[] toBytes() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(type);
        buf.writeLong(playerUuid.getMostSignificantBits());
        buf.writeLong(playerUuid.getLeastSignificantBits());
        buf.writeLong(sequence);
        buf.writeInt(data.length);
        buf.writeBytes(data);
        byte[] result = new byte[buf.readableBytes()];
        buf.readBytes(result);
        return result;
    }

    public static VoicePacket fromBytes(byte[] bytes) {
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        int type = buf.readInt();
        UUID uuid = new UUID(buf.readLong(), buf.readLong());
        long seq = buf.readLong();
        int len = buf.readInt();
        byte[] data = new byte[len];
        buf.readBytes(data);
        return new VoicePacket(type, uuid, seq, data);
    }
}