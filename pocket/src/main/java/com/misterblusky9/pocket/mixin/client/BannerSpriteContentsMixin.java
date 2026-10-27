package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.client.PocketTabBanner;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteTicker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpriteContents.class)
public abstract class BannerSpriteContentsMixin implements PocketTabBanner.TickerHolder {
    @Unique
    private SpriteTicker pocket$ticker;

    @Override
    public SpriteTicker pocket$getTicker() {
        return pocket$ticker;
    }

    @Inject(method = "createTicker", at = @At("RETURN"), require = 1)
    private void pocket$keepTicker(CallbackInfoReturnable<SpriteTicker> cir) {
        pocket$ticker = cir.getReturnValue();
    }
}
