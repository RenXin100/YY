package com.renxin.server.mixin;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPlayNetworkHandler.class)
public interface ServerPlayNetworkHandlerAccessor {
    // 自动生成 getter，把 private 字段暴露出来
    @Accessor("connection")
    ClientConnection renvoice$getConnection();
}