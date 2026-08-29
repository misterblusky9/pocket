package com.misterblusky9.pocket.mixin.sable;

import com.misterblusky9.pocket.moon.MoonSubLevel;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Sable validates that a sub-level's constraint anchor lies inside its plot, because for
// a real sub-level the Rapier body's own frame IS plot space. The moon's body is a box
// created in world space, so its anchors are body-local by design and would never pass.
//
// RapierFreeConstraintHandle.create passes pos1/pos2 to native untouched, so handing it
// plot coordinates puts the joint 20 million blocks off its body - which is what threw
// the sticker's craft into an extreme Y range and got it deleted.
@Mixin(value = PhysicsConstraintConfiguration.class, remap = false)
public interface MoonConstraintAnchorMixin {
    @Inject(method = "validateAnchors", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void pocket$skipMoonAnchorValidation(
            final ServerSubLevelContainer container,
            final PhysicsPipelineBody bodyA,
            final PhysicsPipelineBody bodyB,
            final Vector3dc pos1,
            final Vector3dc pos2,
            final CallbackInfo ci
    ) {
        if (bodyA instanceof MoonSubLevel || bodyB instanceof MoonSubLevel) ci.cancel();
    }
}
