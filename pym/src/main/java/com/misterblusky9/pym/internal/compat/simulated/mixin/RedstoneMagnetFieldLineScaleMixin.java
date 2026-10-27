package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.internal.compat.simulated.MagnetFieldEmitterScale;
import com.misterblusky9.pym.internal.compat.simulated.ScaledMagnetField;
import dev.simulated_team.simulated.content.blocks.redstone_magnet.RedstoneMagnetBlockEntity;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.HashSet;

@Mixin(value = RedstoneMagnetBlockEntity.class, remap = false)
public abstract class RedstoneMagnetFieldLineScaleMixin {
    @ModifyArg(
            method = "spawnParticles",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Quaterniond;transform(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;",
                    ordinal = 0
            ),
            remap = false
    )
    private Vector3d pym$scaleLineStart(Vector3d offset) {
        return offset.mul(this.pym$scale());
    }

    @WrapOperation(
            method = "spawnParticles",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/simulated_team/simulated/content/blocks/redstone_magnet/RedstoneMagnetBlockEntity;"
                            + "setMagneticMoment(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"
            ),
            remap = false
    )
    private Vector3d pym$weightMoment(RedstoneMagnetBlockEntity other, Vector3d moment, Operation<Vector3d> original) {
        double scale = ScaledMagnetField.scaleOf(other.getLatestSubLevel());
        return original.call(other, moment).mul(scale * scale * scale);
    }

    @WrapOperation(
            method = "spawnParticles",
            at = @At(value = "INVOKE", target = "Ljava/util/HashSet;add(Ljava/lang/Object;)Z"),
            remap = false
    )
    private boolean pym$scaleEmitter(HashSet<Object> emitters, Object emitter, Operation<Boolean> original) {
        if (emitter instanceof MagnetFieldEmitterScale scaled) scaled.pym$scale(this.pym$scale());
        return original.call(emitters, emitter);
    }

    @Unique
    private double pym$scale() {
        return ScaledMagnetField.scaleOf(((RedstoneMagnetBlockEntity) (Object) this).getLatestSubLevel());
    }
}
