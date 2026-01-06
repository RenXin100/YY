package com.renxin;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.renxin.client.audio.MicrophoneRecorder;
import com.renxin.client.audio.SpeakerManager;
import com.renxin.client.input.KeyBindings;
import com.renxin.client.network.UDPClient;
import com.renxin.client.network.VoiceClientNetwork;
import com.renxin.common.network.NetworkConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class RenVoiceClient implements ClientModInitializer {

    // 单例引用
    public static RenVoiceClient INSTANCE;

    // 核心组件
    public UDPClient udpClient; // 注意：UDPClient 类要在 client.network 包下
    public SpeakerManager speakerManager;
    public MicrophoneRecorder microphoneRecorder;

    // 按键绑定
    public static KeyBinding PUSH_TO_TALK_KEY;
    public static KeyBinding OPEN_MENU_KEY;

    // 目标地址 (虽然我们会强制覆盖它，但变量得留着)
    public static int TARGET_PORT = NetworkConstants.DEFAULT_VOICE_PORT;
    public static String TARGET_IP = "127.0.0.1";

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        System.out.println("[RenVoice] 正在初始化客户端 (强制本地调试模式)...");

        // 1. 初始化按键
        registerKeyBindings();

        // 2. 初始化音频组件
        try {
            // 确保这些类都有无参构造函数
            speakerManager = new SpeakerManager();
            microphoneRecorder = new MicrophoneRecorder();
            // 注意：MicrophoneRecorder 在之前的修改中改成了无参构造
            // 它的初始化逻辑移到了 startRecording 方法里
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[RenVoice] 致命错误：音频组件初始化失败！");
        }

        // 3. 注册网络接收器
        registerNetworkReceivers();

        // 4. 注册指令
        registerClientCommands();

        // 5. 注册事件
        registerEvents();
    }

    private void registerNetworkReceivers() {
        // 监听 PACKET_CONFIG (路由同步)
        ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.PACKET_CONFIG_ID, (client, handler, buf, responseSender) -> {
            String serverIp = buf.readString();
            int serverPort = buf.readInt();
            String name = buf.readString();

            System.out.println("[RenVoice] 收到服务端推送配置: " + name + " -> " + serverIp + ":" + serverPort);

            client.execute(() -> {
                // 收到配置后，触发重启，但我们会忽略服务端的 IP，强制连本地
                restartVoiceSystem();
                if(client.player != null) {
                    client.player.sendMessage(Text.of("§b[RenVoice] 收到路由配置 (已强制重定向至本地)"), false);
                }
            });
        });
    }

    /**
     * 核心启动方法：强制连接 127.0.0.1:24454
     */
    public void restartVoiceSystem() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            // 1. 停止旧服务
            stopVoiceSystem();

            // === [强制硬编码] ===
            // 不管服务端发来什么，也不管 TCP 连的什么域名
            // 只要是开发环境，强制连本地回环地址！
            String forceIp = "127.0.0.1";
            int forcePort = 24454;
            System.out.println("[RenVoice-Debug] ⚠️ 强制调试模式: 锁定目标 -> " + forceIp + ":" + forcePort);
            // ====================

            // 2. 初始化 UDP 并连接
            // 注意：这里需要你的 UDPClient 有无参构造，或者把 connect 逻辑放进去
            // 假设你的 UDPClient 结构如下：new UDPClient() -> connect()
            if (udpClient != null) {
                try { udpClient.close(); } catch (Exception ignored) {}
            }
            // 重新 new 一个新的 UDPClient 实例，防止旧的状态残留
            udpClient = new com.renxin.client.network.UDPClient();
            udpClient.connect(forceIp, forcePort);

            // 3. 发送握手包
            System.out.println("[RenVoice] 正在发送 UDP 握手包...");
            udpClient.sendHandshake(client.player.getUuid());

            // 4. 启动接收监听 (把包传给 SpeakerManager)
            udpClient.startListening(packet -> {
                if (speakerManager != null) {
                    speakerManager.processPacket(packet);
                }
            });

            // 5. 启动播放器
            if (speakerManager != null) speakerManager.start();

            // 6. 启动录音器
            if (microphoneRecorder != null) {
                // 把 udpClient 传给录音器，让它用这个连接发数据
                // 并且把 speakerManager 也传进去 (如果你的 startRecording 需要的话)
                microphoneRecorder.startRecording(forceIp, forcePort, speakerManager);

                // 【特别注意】如果你的 MicrophoneRecorder 内部自己又 new 了一个 UDPClient
                // 那么上面的 handshake 就白发了！
                // 请务必检查 MicrophoneRecorder.java，确保它要么复用这里的 udpClient
                // 要么它自己 startRecording 的时候也会发握手！
                // 根据上一轮代码，MicrophoneRecorder.startRecording 会自己 new UDPClient 并发握手，所以这里其实是双保险。
            }

            System.out.println("[RenVoice] 语音系统已启动 (本地强制版)");

        } catch (Exception e) {
            e.printStackTrace();
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(Text.of("§c[RenVoice] 连接失败: " + e.getMessage()), false);
            }
        }
    }

    public void stopVoiceSystem() {
        if (microphoneRecorder != null) microphoneRecorder.stopRecording();
        if (speakerManager != null) speakerManager.stop();
        if (udpClient != null) udpClient.close();
    }

    private void registerEvents() {
        // 进服事件 (作为保底，如果服务端没发包，进服后也会尝试连本地)
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // 延迟一点点执行，确保 world 加载完成
            restartVoiceSystem();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            stopVoiceSystem();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (OPEN_MENU_KEY.wasPressed()) {
                client.setScreen(new com.renxin.client.gui.VoiceConfigScreen(client.currentScreen));
            }
        });
    }

    private void registerKeyBindings() {
        PUSH_TO_TALK_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.renvoice.push_to_talk",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V, // 默认 V 键
                "category.renvoice.title"
        ));

        OPEN_MENU_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.renvoice.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K, // 默认 K 键
                "category.renvoice.title"
        ));
    }

    private void registerClientCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("renvoice_port")
                    .then(argument("port", IntegerArgumentType.integer(1, 65535))
                            .executes(context -> {
                                int p = IntegerArgumentType.getInteger(context, "port");
                                context.getSource().sendFeedback(Text.of("§e[注意] 当前处于强制本地调试模式，修改端口无效！"));
                                // TARGET_PORT = p; // 调试模式下禁用修改
                                // restartVoiceSystem();
                                return 1;
                            })
                    )
            );
        });
    }

    public static boolean isPushToTalkPressed() {
        return PUSH_TO_TALK_KEY.isPressed();
    }
}