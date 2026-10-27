package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.client.PocketTabBanner;
import net.minecraft.client.renderer.texture.SpriteTicker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.texture.SpriteContents$Ticker")
public abstract class BannerSpriteTickerMixin implements PocketTabBanner.PausableTicker {
    @Shadow
    int frame;
    @Shadow
    int subFrame;

    @Unique
    private boolean pocket$playing = true;
    @Unique
    private boolean pocket$rewinding;

    @Override
    public void pocket$setPlaying(boolean playing) {
        pocket$playing = playing;
    }

    @Inject(method = "tickAndUpload", at = @At("HEAD"), cancellable = true, require = 1)
    private void pocket$holdFrame(int x, int y, CallbackInfo ci) {
        if (pocket$playing || pocket$rewinding) return;
        ci.cancel();
        if (frame == 0) return;

        pocket$rewinding = true;
        while (frame != 0) {
            subFrame = Integer.MAX_VALUE - 1;
            ((SpriteTicker) (Object) this).tickAndUpload(x, y);
        }
        pocket$rewinding = false;
    }
}
