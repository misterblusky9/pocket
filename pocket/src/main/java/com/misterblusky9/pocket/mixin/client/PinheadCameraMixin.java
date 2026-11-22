package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.client.HatEggs;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class PinheadCameraMixin {
    @Shadow
    private float eyeHeight;

    @Shadow
    private float eyeHeightOld;

    @Shadow
    public abstract Vec3 getPosition();

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "setup", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setPosition(DDD)V", ordinal = 0, shift = At.Shift.AFTER))
    private void pocket$lowerPinhead(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse,
                                     float partialTick, CallbackInfo ci) {
        if (!(entity instanceof LivingEntity living)) return;
        double drop = HatEggs.eyeDrop(living, Mth.lerp(partialTick, eyeHeightOld, eyeHeight));
        if (drop > 0.0D) setPosition(getPosition().subtract(0.0D, drop, 0.0D));
    }
}
