package com.example.orangeclient.mixin;

import com.example.orangeclient.module.ModuleManager;
import com.example.orangeclient.module.combat.KillAura;
import com.example.orangeclient.module.movement.GuiMove;
import com.example.orangeclient.module.player.NoPush;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    /**
     * Vanilla wypycha gracza na zewnatrz, gdy jego hitbox siedzi w bloku
     * (np. po perle). Przy wlaczonej opcji NoPush > Blocks anulujemy to.
     */
    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    private void orange$noBlockPush(double x, double z, CallbackInfo ci) {
        NoPush np = ModuleManager.get(NoPush.class);
        if (np != null && np.blocksBlockPush()) ci.cancel();
    }

    /**
     * GuiMove: tuz przed odczytem inputu ustawiamy prawdziwy stan klawiszy ruchu,
     * bo vanilla zeruje je przy otwarciu ekranu.
     */
    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void orange$guiMove(CallbackInfo ci) {
        GuiMove gm = ModuleManager.get(GuiMove.class);
        if (gm != null && gm.isEnabled()) gm.syncKeys(MinecraftClient.getInstance());
    }

    /** KillAura (silent): rotacja musi byc gotowa ZANIM gracz policzy ruch. */
    @Inject(method = "tick", at = @At("HEAD"))
    private void orange$killAuraTick(CallbackInfo ci) {
        KillAura ka = ModuleManager.get(KillAura.class);
        if (ka != null && ka.isEnabled()) ka.tickRotation(MinecraftClient.getInstance());
    }

    /**
     * KillAura (silent rotation): przed wyslaniem pakietow ruchu podmieniamy yaw/pitch na kierunek celu,
     * a po ich wyslaniu (i ataku) przywracamy prawdziwe katy - kamera gracza sie nie rusza.
     */
    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void orange$killAuraPre(CallbackInfo ci) {
        KillAura ka = ModuleManager.get(KillAura.class);
        if (ka != null && ka.isEnabled()) ka.preMotion(MinecraftClient.getInstance());
    }

    @Inject(method = "sendMovementPackets", at = @At("RETURN"))
    private void orange$killAuraPost(CallbackInfo ci) {
        KillAura ka = ModuleManager.get(KillAura.class);
        if (ka != null) ka.postMotion(MinecraftClient.getInstance());
    }
}
