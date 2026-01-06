package com.renxin.client.config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class VoiceSettings {
    // 单例模式
    private static final VoiceSettings INSTANCE = new VoiceSettings();
    public static VoiceSettings getInstance() { return INSTANCE; }

    // --- 自身状态 ---
    // 模式: true = 常开麦 (Open Mic), false = 按键说话 (PTT)
    private boolean openMicMode = false;

    // PTT键是否被按下 (由按键事件更新)
    private boolean pttKeyPressed = false;

    // --- 对其他玩家的设置 ---
    // 玩家音量 (0.0f - 2.0f, 默认 1.0f)
    private final Map<UUID, Float> playerVolumes = new HashMap<>();
    // 被屏蔽的玩家
    private final Set<UUID> mutedPlayers = new HashSet<>();

    // --- Getter / Setter ---

    public boolean isOpenMicMode() {
        return openMicMode;
    }

    public void setOpenMicMode(boolean openMicMode) {
        this.openMicMode = openMicMode;
    }

    public boolean isPttKeyPressed() {
        return pttKeyPressed;
    }

    public void setPttKeyPressed(boolean pttKeyPressed) {
        this.pttKeyPressed = pttKeyPressed;
    }

    // 判断当前是否应该发送语音
    public boolean shouldSendVoice() {
        if (openMicMode) {
            return true; // 常开模式：一直发
        } else {
            return pttKeyPressed; // PTT模式：按键才发
        }
    }

    public float getPlayerVolume(UUID uuid) {
        return playerVolumes.getOrDefault(uuid, 1.0f);
    }

    public void setPlayerVolume(UUID uuid, float volume) {
        playerVolumes.put(uuid, volume);
    }

    public boolean isPlayerMuted(UUID uuid) {
        return mutedPlayers.contains(uuid);
    }

    public void setPlayerMuted(UUID uuid, boolean muted) {
        if (muted) {
            mutedPlayers.add(uuid);
        } else {
            mutedPlayers.remove(uuid);
        }
    }
    private long muteEndTime = 0; // 0 表示没被禁言

    public void setMuteEndTime(long time) { this.muteEndTime = time; }
    public long getMuteEndTime() { return muteEndTime; }
    public boolean isMuted() { return System.currentTimeMillis() < muteEndTime; }
}