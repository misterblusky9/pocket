package com.misterblusky9.pym.internal.mixin.physics;

import com.misterblusky9.pym.api.PhysicsBodies;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.rope.RapierRopeHandle", remap = false)
public abstract class UnavailableRopeBodyGuardMixin {
    @Inject(method = "setAttachment", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$attachment(RopeHandle.AttachmentPoint point, Vector3dc location, ServerSubLevel subLevel,
                                CallbackInfo ci) {
        if (!PhysicsBodies.available(subLevel)) ci.cancel();
    }
}
