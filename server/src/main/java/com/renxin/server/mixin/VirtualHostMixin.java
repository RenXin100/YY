package com.renxin.server.mixin;

import com.renxin.common.access.IVirtualHostHolder;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.server.network.ServerHandshakeNetworkHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class VirtualHostMixin {

    // 1. ClientConnection 注入保持不变
    @Mixin(ClientConnection.class)
    public static class ConnectionMixin implements IVirtualHostHolder {
        private String renvoice$virtualHost;

        @Override
        public String renvoice$getVirtualHost() { return renvoice$virtualHost; }

        @Override
        public void renvoice$setVirtualHost(String host) { this.renvoice$virtualHost = host; }
    }

    // 2. 握手拦截修复
    @Mixin(ServerHandshakeNetworkHandler.class)
    public static class HandshakeMixin {

        // [修复] 使用 Shadow 获取 private final connection 字段
        @Shadow @Final private ClientConnection connection;

        @Inject(method = "onHandshake", at = @At("HEAD"))
        private void captureHostname(HandshakeC2SPacket packet, CallbackInfo ci) {
            // 使用 Shadow 字段，而不是调用不存在的 getConnection()
            if (this.connection instanceof IVirtualHostHolder holder) {
                holder.renvoice$setVirtualHost(packet.getAddress());
            }
        }
    }
}