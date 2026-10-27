package com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.LinkageLengthScale;
import dev.ryanhcode.sable.ActiveSableCompanion;
import net.minecraft.core.Position;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "com.enxv.aerouniversaljoint.content.UniversalJointBlockEntity", remap = false)
public abstract class UniversalJointLengthMixin {
    @WrapOperation(method = "distanceSquaredTo", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/ActiveSableCompanion;distanceSquaredWithSubLevels("
                    + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/Position;"
                    + "Lnet/minecraft/core/Position;)D"), remap = false)
    private double pym$nominalLinkDistance(
            ActiveSableCompanion helper, Level level, Position first, Position second,
            Operation<Double> original
    ) {
        double scale = LinkageLengthScale.between(level, first, second);
        return original.call(helper, level, first, second) / (scale * scale);
    }

    @ModifyExpressionValue(method = "sable$physicsTick", at = @At(value = "INVOKE",
            target = "Lcom/enxv/aerouniversaljoint/content/UniversalJointBlockEntity;toWorldPosition("
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"),
            remap = false)
    private Vector3d pym$nominalEndpoint(Vector3d world) {
        return LinkageLengthScale.toNominal(world, LinkageLengthScale.ofLink((BlockEntity) (Object) this));
    }

    @ModifyExpressionValue(method = {"sable$physicsTick", "applyAndesiteElasticity"}, at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/ActiveSableCompanion;getVelocity("
                    + "Lnet/minecraft/world/level/Level;Lorg/joml/Vector3dc;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"),
            remap = false)
    private Vector3d pym$nominalVelocity(Vector3d world) {
        return LinkageLengthScale.toNominal(world, LinkageLengthScale.ofLink((BlockEntity) (Object) this));
    }

    @ModifyVariable(method = "applyElasticImpulse", at = @At("HEAD"), argsOnly = true, ordinal = 2, remap = false)
    private Vector3d pym$metricImpulse(Vector3d impulse) {
        double scale = LinkageLengthScale.ofLink((BlockEntity) (Object) this);
        return scale == 1.0D ? impulse : new Vector3d(impulse).mul(scale);
    }
}
