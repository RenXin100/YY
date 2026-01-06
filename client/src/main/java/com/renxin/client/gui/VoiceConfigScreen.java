package com.renxin.client.gui;

import com.renxin.client.config.VoiceSettings;
import com.renxin.client.input.KeyBindings;
import com.renxin.client.network.VoiceClientNetwork;
import com.renxin.client.state.ClientChannelManager;
import com.renxin.common.network.NetworkConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.*;

public class VoiceConfigScreen extends Screen {

    private final Screen parent;

    // --- 图标与布局 ---
    private static final String ICON_UNMUTED = "🔊";
    private static final String ICON_MUTED = "🔈";

    private static final int LEFT_PANEL_WIDTH = 100;
    private static final int RIGHT_PANEL_START_X = LEFT_PANEL_WIDTH + 10;
    private static final int ROW_HEIGHT = 28; // 稍微增加行高，防止拥挤
    private static final int HEADER_HEIGHT = 40; // 顶部留空高度

    // --- 滚动与交互状态 ---
    private double scrollOffset = 0;
    private int maxScroll = 0;
    private int listBottomY;

    // 专门记录当前正在被拖拽的条目，解决“滑不动”的问题
    private PlayerEntry draggingEntry = null;

    private final List<PlayerEntry> playerEntries = new ArrayList<>();

    public VoiceConfigScreen(Screen parent) {
        super(Text.of("RenVoice"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.listBottomY = this.height - 35;
        this.playerEntries.clear();
        this.scrollOffset = 0;
        this.draggingEntry = null;

        // 1. 右上角：模式切换
        boolean isOpenMic = VoiceSettings.getInstance().isOpenMicMode();
        addDrawableChild(ButtonWidget.builder(
                Text.of(isOpenMic ? "当前: 常开麦" : "当前: 按键说话"),
                button -> {
                    boolean newState = !VoiceSettings.getInstance().isOpenMicMode();
                    VoiceSettings.getInstance().setOpenMicMode(newState);
                    button.setMessage(Text.of(newState ? "当前: 常开麦" : "当前: 按键说话"));
                }
        ).dimensions(this.width - 110, 10, 100, 20).build());

        // 2. 初始化列表
        initPlayerEntries();

        // 3. 关闭按钮
        addDrawableChild(ButtonWidget.builder(Text.of("关闭"), button -> close())
                .dimensions(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    private void initPlayerEntries() {
        String myChannel = ClientChannelManager.getInstance().getCurrentChannel();
        Collection<PlayerListEntry> allPlayers = MinecraftClient.getInstance().getNetworkHandler().getPlayerList();
        UUID myUuid = MinecraftClient.getInstance().player.getUuid();

        // --- 步骤 A: 筛选同频道玩家 ---
        List<PlayerListEntry> validPlayers = new ArrayList<>();
        PlayerListEntry me = null;

        for (PlayerListEntry p : allPlayers) {
            UUID uuid = p.getProfile().getId();
            String pChannel = ClientChannelManager.getInstance().getPlayerChannel(uuid);

            // 只看同频道的
            if (!pChannel.equals(myChannel)) continue;

            if (uuid.equals(myUuid)) {
                me = p; // 找到自己，先存起来
            } else {
                validPlayers.add(p);
            }
        }

        // --- 步骤 B: 排序与构建 (自己永远在第一个) ---
        // 1. 先加自己
        if (me != null) {
            addEntry(me, true);
        } else {
            // 如果列表里没抓到自己(极罕见)，手动造一个假的显示条目防空
            // (通常不会发生，除非刚进服数据没同步)
        }

        // 2. 再加其他人 (按名字排序)
        validPlayers.sort(Comparator.comparing(p -> p.getProfile().getName()));
        for (PlayerListEntry p : validPlayers) {
            addEntry(p, false);
        }

        // 计算最大滚动范围
        int contentHeight = playerEntries.size() * ROW_HEIGHT;
        int viewHeight = listBottomY - HEADER_HEIGHT;
        this.maxScroll = Math.max(0, contentHeight - viewHeight);
    }

    private void addEntry(PlayerListEntry player, boolean isSelf) {
        UUID uuid = player.getProfile().getId();
        String name = player.getProfile().getName();
        if (isSelf) name += " (我)";

        float currentVol = VoiceSettings.getInstance().getPlayerVolume(uuid);
        boolean isMuted = VoiceSettings.getInstance().isPlayerMuted(uuid);

        int sliderX = RIGHT_PANEL_START_X + 100; // 滑块往右挪一点，给名字留空间
        int sliderWidth = 100;
        int btnX = sliderX + sliderWidth + 5;

        // 创建滑块
        double initialVal = isMuted ? 0.0 : (currentVol / 2.0);
        VolumeSlider slider = new VolumeSlider(sliderX, 0, sliderWidth, 20, initialVal, uuid);

        // 创建按钮
        ButtonWidget muteBtn = ButtonWidget.builder(
                Text.of(isMuted ? ICON_MUTED : ICON_UNMUTED),
                button -> {
                    boolean nowMuted = !VoiceSettings.getInstance().isPlayerMuted(uuid);
                    VoiceSettings.getInstance().setPlayerMuted(uuid, nowMuted);
                    button.setMessage(Text.of(nowMuted ? ICON_MUTED : ICON_UNMUTED));

                    if (nowMuted) {
                        slider.forceSetValue(0.0);
                    } else {
                        float savedVol = VoiceSettings.getInstance().getPlayerVolume(uuid);
                        if (savedVol <= 0.01f) savedVol = 1.0f;
                        VoiceSettings.getInstance().setPlayerVolume(uuid, savedVol);
                        slider.forceSetValue(savedVol / 2.0);
                    }
                }
        ).dimensions(btnX, 0, 20, 20).build();
        // 3. 关键修复：把按钮传给滑块，让滑块拖动时能更新按钮
        slider.setLinkedButton(muteBtn);

        playerEntries.add(new PlayerEntry(uuid, name, slider, muteBtn));
    }

    // --- 渲染 ---

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        // 背景
        context.fill(0, 0, LEFT_PANEL_WIDTH, this.height, 0x80000000);
        context.drawVerticalLine(LEFT_PANEL_WIDTH, 0, this.height, 0xFFFFFFFF);

        // 列表
        drawChannelList(context, mouseX, mouseY);

        // 标题
        String myChannel = ClientChannelManager.getInstance().getCurrentChannel();
        context.drawTextWithShadow(this.textRenderer, "频道成员: " + myChannel, RIGHT_PANEL_START_X, 15, 0xFFFFFF);

        // --- 核心：使用 enableScissor 进行裁剪 ---
        // 1.20.1 的 enableScissor 参数通常是 (x1, y1, x2, y2) 即左上角和右下角坐标
        // 或者是 (x, y, w, h)，这取决于 Fabric API / Yarn 的映射
        // 保险起见，我们使用 GL 裁剪逻辑的封装

        int scissorY = HEADER_HEIGHT;
        int scissorBottom = listBottomY;

        // 开启裁剪：只在列表区域显示内容
        context.enableScissor(LEFT_PANEL_WIDTH, scissorY, this.width, scissorBottom);

        context.getMatrices().push();
        context.getMatrices().translate(0, -scrollOffset, 0);

        int currentY = HEADER_HEIGHT; // 从这里开始排列

        for (PlayerEntry entry : playerEntries) {
            // 简单优化：只绘制视野内的
            if (currentY + ROW_HEIGHT - scrollOffset >= HEADER_HEIGHT && currentY - scrollOffset <= listBottomY) {
                // 名字
                context.drawTextWithShadow(this.textRenderer, entry.name, RIGHT_PANEL_START_X, currentY + 6, 0xFFFFFF);

                // 控件 (必须先设置Y再渲染)
                entry.slider.setY(currentY);
                entry.muteBtn.setY(currentY);

                // 修正鼠标坐标传入控件，让悬停效果正常
                // 因为我们用了 translate，这里的 mouseY 相对控件是“偏移”了的
                // 实际上最稳妥的方式是：传递真实的 mouseX, mouseY + scrollOffset
                entry.slider.render(context, mouseX, (int)(mouseY + scrollOffset), delta);
                entry.muteBtn.render(context, mouseX, (int)(mouseY + scrollOffset), delta);
            }
            currentY += ROW_HEIGHT;
        }

        context.getMatrices().pop();
        context.disableScissor();

        super.render(context, mouseX, mouseY, delta);
    }

    // --- 交互 (核心修复：解决滑不动的问题) ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 1. 左侧频道点击
        if (mouseX < LEFT_PANEL_WIDTH && mouseY >= 40) {
            handleChannelClick(mouseY);
            return true;
        }

        // 2. 右侧点击 (计算滚动后的坐标)
        // 只有鼠标在列表区域内，才允许发起点击
        if (mouseX > LEFT_PANEL_WIDTH && mouseY >= HEADER_HEIGHT && mouseY <= listBottomY) {
            double scrolledY = mouseY + scrollOffset;

            for (PlayerEntry entry : playerEntries) {
                // 检查滑块
                if (entry.slider.mouseClicked(mouseX, scrolledY, button)) {
                    // 【关键】锁定这个滑块！后续拖动全给它
                    this.draggingEntry = entry;
                    return true;
                }
                // 检查按钮
                if (entry.muteBtn.mouseClicked(mouseX, scrolledY, button)) {
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // 1. 如果有锁定的滑块，直接发给它 (无视鼠标位置)
        if (this.draggingEntry != null) {
            return this.draggingEntry.slider.mouseDragged(mouseX, mouseY + scrollOffset, button, deltaX, deltaY);
        }

        // 2. 否则处理滚动条逻辑 (鼠标在右侧区域)
        if (mouseX > LEFT_PANEL_WIDTH) {
            // 简单的拖拽滚动 (可选，如果觉得滚轮够用可以不加这个)
            // return super.mouseDragged(...)
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // 释放时，解锁滑块
        if (this.draggingEntry != null) {
            this.draggingEntry.slider.mouseReleased(mouseX, mouseY + scrollOffset, button);
            this.draggingEntry = null;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX > LEFT_PANEL_WIDTH) {
            this.scrollOffset = MathHelper.clamp(this.scrollOffset - amount * 15, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    // --- 辅助方法 ---

    private void handleChannelClick(double mouseY) {
        List<String> channels = new ArrayList<>(ClientChannelManager.getInstance().getChannels());
        // 排序确保 Public 第一
        if (channels.contains(NetworkConstants.CHANNEL_PUBLIC)) {
            channels.remove(NetworkConstants.CHANNEL_PUBLIC);
            channels.add(0, NetworkConstants.CHANNEL_PUBLIC);
        } else {
            channels.add(0, NetworkConstants.CHANNEL_PUBLIC);
        }

        int index = (int) ((mouseY - 40) / 20);
        if (index >= 0 && index < channels.size()) {
            String target = channels.get(index);
            if (!target.equals(ClientChannelManager.getInstance().getCurrentChannel())) {
                openConfirmation(target);
            }
        }
    }

    private void drawChannelList(DrawContext context, int mouseX, int mouseY) {
        int y = 40;
        List<String> channels = new ArrayList<>(ClientChannelManager.getInstance().getChannels());
        if (channels.contains(NetworkConstants.CHANNEL_PUBLIC)) {
            channels.remove(NetworkConstants.CHANNEL_PUBLIC);
            channels.add(0, NetworkConstants.CHANNEL_PUBLIC);
        } else {
            channels.add(0, NetworkConstants.CHANNEL_PUBLIC);
        }

        String current = ClientChannelManager.getInstance().getCurrentChannel();
        context.drawCenteredTextWithShadow(this.textRenderer, "频道列表", LEFT_PANEL_WIDTH / 2, 20, 0xFFFF00);

        for (String c : channels) {
            boolean isSelected = c.equals(current);
            boolean isHovered = mouseX >= 0 && mouseX <= LEFT_PANEL_WIDTH && mouseY >= y && mouseY < y + 20;

            if (isSelected) context.fill(5, y - 2, LEFT_PANEL_WIDTH - 5, y + 12, 0x6000FF00);
            else if (isHovered) context.fill(5, y - 2, LEFT_PANEL_WIDTH - 5, y + 12, 0x40FFFFFF);

            String txt = c.equals(NetworkConstants.CHANNEL_PUBLIC) ? "🌐 " + c : "🔒 " + c;
            context.drawTextWithShadow(this.textRenderer, txt, 10, y, isSelected ? 0x00FF00 : 0xFFFFFF);
            y += 20;
        }
    }

    private void openConfirmation(String targetChannel) {
        ConfirmScreen s = new ConfirmScreen((confirmed) -> {
            if (confirmed) VoiceClientNetwork.sendJoinRequest(targetChannel);
            this.client.setScreen(this);
        }, Text.of("加入频道"), Text.of("确定加入 [" + targetChannel + "] 吗？"));
        this.client.setScreen(s);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KeyBindings.KEY_OPEN_CONFIG.matchesKey(keyCode, scanCode)) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    // --- 内部类 ---
    private static class PlayerEntry {
        final UUID uuid;
        final String name;
        final VolumeSlider slider;
        final ButtonWidget muteBtn;
        public PlayerEntry(UUID u, String n, VolumeSlider s, ButtonWidget m) {
            this.uuid = u; this.name = n; this.slider = s; this.muteBtn = m;
        }
    }

    private static class VolumeSlider extends SliderWidget {
        private final UUID uuid;
        private ButtonWidget linkedButton; // 新增：持有按钮的引用

        public VolumeSlider(int x, int y, int w, int h, double v, UUID u) {
            super(x, y, w, h, Text.of(""), v);
            this.uuid = u;
            this.updateMessage();
        }

        public void setLinkedButton(ButtonWidget btn) {
            this.linkedButton = btn;
        }

        @Override protected void updateMessage() {
            int percent = (int)(this.value * 200);
            this.setMessage(Text.of("音量: " + percent + "%"));
        }

        @Override protected void applyValue() {
            // 双向联动逻辑
            if (this.value > 0) {
                // 如果之前是静音，现在拉起来了 -> 解除静音
                if (VoiceSettings.getInstance().isPlayerMuted(uuid)) {
                    VoiceSettings.getInstance().setPlayerMuted(uuid, false);
                    if (linkedButton != null) linkedButton.setMessage(Text.of(ICON_UNMUTED));
                }
            } else {
                // 如果拉到0 -> 自动静音
                if (!VoiceSettings.getInstance().isPlayerMuted(uuid)) {
                    VoiceSettings.getInstance().setPlayerMuted(uuid, true);
                    if (linkedButton != null) linkedButton.setMessage(Text.of(ICON_MUTED));
                }
            }
            VoiceSettings.getInstance().setPlayerVolume(uuid, (float)(this.value * 2.0));
        }

        public void forceSetValue(double v) {
            this.value = v; this.updateMessage(); this.applyValue();
        }
    }
}