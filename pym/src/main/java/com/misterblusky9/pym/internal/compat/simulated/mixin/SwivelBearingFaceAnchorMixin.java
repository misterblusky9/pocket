package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SwivelBearingBlockEntity.class, remap = false)
public abstract class SwivelBearingFaceAnchorMixin {
    @WrapOperation(
            method = "attachConstraints",
            at = @At(
                    value = "NEW",
                    target = "(Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)"
                            + "Ldev/ryanhcode/sable/api/physics/constraint/RotaryConstraintConfiguration;",
                    remap = false
            ),
            remap = false
    )
    private RotaryConstraintConfiguration pym$anchorAtFaces(
            final Vector3dc pos1,
            final Vector3dc pos2,
            final Vector3dc normal1,
            final Vector3dc normal2,
            final Operation<RotaryConstraintConfiguration> original
    ) {
        return original.call(
                new Vector3d(normal1).mul(-0.75D).add(pos1),
                new Vector3d(normal2).mul(-0.75D).add(pos2),
                normal1,
                normal2);
    }
}
