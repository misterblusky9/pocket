package com.misterblusky9.pocket.mixin.simulated;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.client.MoonPickTarget;
import net.minecraft.core.Vec3i;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// The drag anchor is normally the centre of the block that was clicked, in plot space.
// The moon has no blocks and its Rapier body is a box in world space, so the anchor has
// to be the exact hit point in the body's own frame - which is also what gets sent to
// the server, drawn as the beam endpoint, and used for the grab distance.
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler", remap = false)
public abstract class PhysicsStaffMoonAnchorMixin {
    @WrapOperation(
            method = "startDraggingSubLevel",
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/companion/math/JOMLConversion;atCenterOf(Lnet/minecraft/core/Vec3i;)Lorg/joml/Vector3d;"),
            remap = false,
            require = 1
    )
    private Vector3d pocket$moonAnchor(final Vec3i blockPos, final Operation<Vector3d> original) {
        final Vector3d moon = MoonPickTarget.lastBodyLocalHit(blockPos);
        return moon != null ? moon : original.call(blockPos);
    }
}
