package com.misterblusky9.pym.internal.compat.simulatedcoasters.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterPlacementScaleContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.cart.CoasterCartPlacementCollision", remap = false)
public abstract class CoasterCartPlacementCollisionScaleMixin {
    @Unique
    private static final Object PYM$COLLISION_LOCK = new Object();

    @WrapMethod(
            method = "hasSubLevelObstruction(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lorg/joml/Quaterniond;Ljava/lang/Float;)Z"
    )
    private static boolean pym$scopeCollisionScale(
            final Level level,
            final Vec3 plotOriginWorld,
            final Quaterniond orientation,
            final Float partialTickForRender,
            final Operation<Boolean> original
    ) {
        synchronized (PYM$COLLISION_LOCK) {
            CoasterPlacementScaleContext.push(CoasterPlacementScaleContext.remembered());
            try {
                return original.call(level, plotOriginWorld, orientation, partialTickForRender);
            } finally {
                CoasterPlacementScaleContext.pop();
            }
        }
    }

    @ModifyArg(
            method = "obbForBodyCellBox([DLdev/ryanhcode/sable/companion/math/Pose3d;Lorg/joml/Quaterniondc;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/companion/math/Pose3d;transformPosition(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"
            ),
            index = 0,
            require = 1
    )
    private static Vector3d pym$scaleProposedBogeyCenter(final Vector3d original) {
        final double scale = CoasterPlacementScaleContext.current();
        if (!ScaleBounds.isValid(scale) || Math.abs(scale - 1.0D) <= ScaleBounds.EPSILON) {
            return original;
        }

        final double s = ScaleBounds.clampValid(scale);
        return new Vector3d(
                0.5D + (original.x - 0.5D) * s,
                0.5D + (original.y - 0.5D) * s,
                0.5D + (original.z - 0.5D) * s
        );
    }

    @ModifyArg(
            method = "obbForBodyCellBox([DLdev/ryanhcode/sable/companion/math/Pose3d;Lorg/joml/Quaterniondc;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;<init>(Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;Lorg/joml/Quaterniondc;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)V"
            ),
            index = 1,
            require = 1
    )
    private static Vector3dc pym$scaleProposedBogeySize(final Vector3dc original) {
        final double scale = CoasterPlacementScaleContext.current();
        if (!ScaleBounds.isValid(scale) || Math.abs(scale - 1.0D) <= ScaleBounds.EPSILON) {
            return original;
        }
        return new Vector3d(original).mul(ScaleBounds.clampValid(scale));
    }
}
