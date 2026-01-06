package com.renxin.server;

import com.renxin.server.network.UDPServer;
import com.renxin.server.network.VoiceServerNetwork;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
// [修复] 补全这个导入
import net.minecraft.command.argument.EntityArgumentType;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.server.command.CommandManager.*;

public class RenVoiceServer implements DedicatedServerModInitializer {
    // ... (代码逻辑与之前给出的完全一致，只要加上上面的import就不会报错了)
    // 为避免刷屏，请保留之前的类主体内容，只添加 import 即可。
    // 如果你之前的被覆盖乱了，请告诉我，我再发一次完整的。

    private UDPServer udpServer;

    @Override
    public void onInitializeServer() {
        ServerChannelConfig.getInstance().load();
        ServerRouteConfig.getInstance().load();
        VoiceServerNetwork.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            udpServer = new UDPServer();
            udpServer.start();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { if (udpServer != null) udpServer.stopServer(); });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            server.execute(() -> {
                VoiceServerNetwork.broadcastSync(server);
                long endTime = ServerChannelConfig.getInstance().getMuteEndTime(handler.player.getUuid());
                if (endTime > System.currentTimeMillis()) VoiceServerNetwork.sendMuteUpdate(handler.player, endTime);
                VoiceServerNetwork.sendRouteConfig(handler.player);
            });
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerChannelManager.getInstance().onPlayerDisconnect(handler.player.getUuid());
            server.execute(() -> VoiceServerNetwork.broadcastSync(server));
        });

        registerCommands();
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("renvoice")
                    .then(literal("create").requires(s -> s.hasPermissionLevel(2))
                            .then(argument("name", greedyString()).executes(c -> {
                                String name = StringArgumentType.getString(c, "name");
                                if(ServerChannelManager.getInstance().createChannel(name, c.getSource().getPlayer())) {
                                    c.getSource().sendMessage(Text.of("§a创建成功: " + name));
                                    VoiceServerNetwork.broadcastSync(c.getSource().getServer());
                                } else c.getSource().sendError(Text.of("失败"));
                                return 1;
                            })))
                    .then(literal("remove").requires(s -> s.hasPermissionLevel(2))
                            .then(argument("name", greedyString()).executes(c -> {
                                String name = StringArgumentType.getString(c, "name");
                                if(ServerChannelManager.getInstance().removeChannel(name)) {
                                    c.getSource().sendMessage(Text.of("§e删除成功"));
                                    VoiceServerNetwork.broadcastSync(c.getSource().getServer());
                                } else c.getSource().sendError(Text.of("失败"));
                                return 1;
                            })))
                    .then(literal("invite").requires(s -> s.hasPermissionLevel(2))
                            .then(argument("target", EntityArgumentType.player())
                                    .then(argument("channel", greedyString()).executes(c -> {
                                        ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "target");
                                        String ch = StringArgumentType.getString(c, "channel");
                                        if(ServerChannelManager.getInstance().invitePlayer(ch, p.getUuid())) {
                                            c.getSource().sendMessage(Text.of("§a已邀请"));
                                            p.sendMessage(Text.of("§e被邀请加入: " + ch), false);
                                        } else c.getSource().sendError(Text.of("频道不存在"));
                                        return 1;
                                    }))))
                    .then(literal("mute").requires(s -> s.hasPermissionLevel(2))
                            .then(argument("target", EntityArgumentType.player())
                                    .then(argument("minutes", IntegerArgumentType.integer(1)).executes(c -> {
                                        ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "target");
                                        int m = IntegerArgumentType.getInteger(c, "minutes");
                                        ServerChannelConfig.getInstance().mutePlayer(p.getUuid(), m);
                                        VoiceServerNetwork.sendMuteUpdate(p, ServerChannelConfig.getInstance().getMuteEndTime(p.getUuid()));
                                        c.getSource().sendMessage(Text.of("§c已禁言"));
                                        return 1;
                                    }))))
                    .then(literal("unmute").requires(s -> s.hasPermissionLevel(2))
                            .then(argument("target", EntityArgumentType.player()).executes(c -> {
                                ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "target");
                                ServerChannelConfig.getInstance().unmutePlayer(p.getUuid());
                                VoiceServerNetwork.sendMuteUpdate(p, 0);
                                c.getSource().sendMessage(Text.of("§a已解禁"));
                                return 1;
                            })))
            );
        });
    }
}