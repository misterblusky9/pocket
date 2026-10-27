package com.misterblusky9.pym.internal.compat.create.mixin;

import com.misterblusky9.pym.internal.compat.create.SchematicToolScale;
import com.simibubi.create.content.schematics.client.tools.SchematicToolBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = SchematicToolBase.class, remap = false)
public abstract class SchematicToolScaleMixin {
    @ModifyArg(
            method = "updateTargetPos",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;"),
            remap = false,
            require = 1
    )
    private double pym$scaleFreeAimRange(double range) {
        return SchematicToolScale.distance(range);
    }

    @ModifyArg(
            method = "updateTargetPos",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/utility/RaycastHelper;rayTraceRange("
                            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;D)"
                            + "Lnet/minecraft/world/phys/BlockHitResult;"
            ),
            index = 2,
            remap = false,
            require = 1
    )
    private double pym$scaleTargetReach(double range) {
        return SchematicToolScale.distance(range);
    }

    @ModifyArg(
            method = "updateTargetPos",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/utility/RaycastHelper;getTraceTarget("
                            + "Lnet/minecraft/world/entity/player/Player;DLnet/minecraft/world/phys/Vec3;)"
                            + "Lnet/minecraft/world/phys/Vec3;"
            ),
            index = 1,
            remap = false,
            require = 1
    )
    private double pym$scaleSchematicReach(double range) {
        return SchematicToolScale.distance(range);
    }

    @ModifyArg(
            method = "renderOnSchematic",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/createmod/catnip/outliner/Outline$OutlineParams;lineWidth(F)"
                            + "Lnet/createmod/catnip/outliner/Outline$OutlineParams;"
            ),
            remap = false,
            require = 1
    )
    private float pym$scaleSchematicLine(float width) {
        return SchematicToolScale.lineWidth(width);
    }
}
