package com.renxin.client.input;

import com.renxin.client.config.VoiceSettings;
import com.renxin.client.gui.VoiceConfigScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {

    public static KeyBinding KEY_PTT;
    public static KeyBinding KEY_OPEN_CONFIG;

    // 防止提示信息刷屏的冷却计时器
    private static long lastWarningTime = 0;

    public static void register() {
        // 1. 注册按键说话键 (默认 V)
        KEY_PTT = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.renvoice.ptt",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.renvoice.title"
        ));

        // 2. 注册打开设置键 (默认 K)
        KEY_OPEN_CONFIG = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.renvoice.config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.renvoice.title"
        ));

        // 3. 注册 Tick 事件监听 (每帧检查按键)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // --- 处理 PTT 按键逻辑 ---
            boolean isPttDown = KEY_PTT.isPressed();

            // 核心逻辑：检查是否被禁言
            if (isPttDown && VoiceSettings.getInstance().isMuted()) {
                long now = System.currentTimeMillis();

                // 冷却检查：每 3000 毫秒 (3秒) 只提示一次
                if (now - lastWarningTime > 3000) {
                    // 计算剩余时间
                    long remainingMillis = VoiceSettings.getInstance().getMuteEndTime() - now;
                    long remainingMins = remainingMillis / 1000 / 60;

                    // 优化显示文本
                    String timeStr = remainingMins <= 0 ? "少于 1" : String.valueOf(remainingMins);

                    // 发送红色警告信息 (true 表示显示在物品栏上方，而不是聊天框，防刷屏)
                    client.player.sendMessage(Text.of("§c[系统] 你已被禁言，剩余时间: " + timeStr + " 分钟"), true);

                    lastWarningTime = now;
                }

                // 重点：强制将 PTT 状态设为 false，拦截语音发送
                VoiceSettings.getInstance().setPttKeyPressed(false);
            } else {
                // 正常情况：直接同步按键状态
                VoiceSettings.getInstance().setPttKeyPressed(isPttDown);
            }

            // --- 处理打开 UI 逻辑 ---
            while (KEY_OPEN_CONFIG.wasPressed()) {
                // 打开我们的配置界面
                client.setScreen(new VoiceConfigScreen(client.currentScreen));
            }
        });
    }
}