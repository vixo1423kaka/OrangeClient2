package com.example.orangeclient.mixin;

import com.example.orangeclient.module.render.NameProtect;
import net.minecraft.text.TextVisitFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TextVisitFactory.class)
public abstract class TextVisitFactoryMixin {
    /**
     * NameProtect: kazdy tekst rysowany/mierzony przez gre przechodzi przez ta metode
     * (pozostale przeciazenia visitFormatted wolaja ja na koncu).
     */
    @ModifyVariable(
            method = "visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z",
            at = @At("HEAD"), argsOnly = true)
    private static String orange$nameProtect(String text) {
        return NameProtect.apply(text);
    }
}
