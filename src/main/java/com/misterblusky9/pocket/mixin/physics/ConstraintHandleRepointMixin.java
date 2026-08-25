package com.misterblusky9.pocket.mixin.physics;

import com.misterblusky9.pocket.physics.RepointableConstraint;
import com.misterblusky9.pocket.physics.RapierSceneLifetime;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.constraint.RapierConstraintHandle", remap = false)
public abstract class ConstraintHandleRepointMixin implements RepointableConstraint {
    @Mutable
    @Shadow
    @Final
    protected long handle;

    @Shadow
    @Final
    protected long sceneHandle;

    @Unique
    private RapierSceneLifetime.Token pocket$sceneToken;

    @Unique
    private boolean pocket$knownRemoved;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void pocket$captureSceneGeneration(
            final long sceneHandle,
            final long nativeHandle,
            final CallbackInfo ci
    ) {
        this.pocket$sceneToken = RapierSceneLifetime.tokenFor(sceneHandle);
    }

    @Override
    public long pocket$nativeHandle() {
        return this.handle;
    }

    @Override
    public long pocket$sceneHandle() {
        return this.sceneHandle;
    }

    @Override
    public boolean pocket$isSceneLive() {
        return RapierSceneLifetime.isLive(this.pocket$sceneToken, this.sceneHandle);
    }

    @Override
    public boolean pocket$isKnownRemoved() {
        return this.pocket$knownRemoved;
    }

    @Override
    public void pocket$markRemoved() {
        this.pocket$knownRemoved = true;
    }

    @Override
    public void pocket$repoint(final long nativeHandle) {
        this.handle = nativeHandle;
        this.pocket$knownRemoved = false;
    }
}
