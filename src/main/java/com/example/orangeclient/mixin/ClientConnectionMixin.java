package com.example.orangeclient.mixin;

import com.example.orangeclient.module.ModuleManager;
import com.example.orangeclient.module.movement.FakeLag;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {
    /** FakeLag: przejmuje pakiety wychodzace (kazda sciezka send() konczy sie w tej metodzie). */
    @Inject(
            method = "send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void orange$fakeLag(Packet<?> packet, PacketCallbacks callbacks, boolean flush, CallbackInfo ci) {
        FakeLag fl = ModuleManager.get(FakeLag.class);
        if (fl != null && fl.isEnabled() && fl.intercept((ClientConnection) (Object) this, packet, callbacks)) ci.cancel();
    }
}
