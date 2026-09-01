package com.misterblusky9.pocket.mixin.simulatedcoasters;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersPlacementScaleContext;
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
    private static final Object POCKET$COLLISION_LOCK = new Object();

    @WrapMethod(
            method = "hasSubLevelObstruction(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lorg/joml/Quaterniond;Ljava/lang/Float;)Z"
    )
    private static boolean pocket$serializeCollisionScratchAndScopeScale(
            final Level level,
            final Vec3 plotOriginWorld,
            final Quaterniond orientation,
            final Float partialTickForRender,
            final Operation<Boolean> original
    ) {
        synchronized (POCKET$COLLISION_LOCK) {
            final double scale = SimulatedCoastersPlacementScaleContext.remembered();
            SimulatedCoastersPlacementScaleContext.push(scale);
            try {
                return original.call(level, plotOriginWorld, orientation, partialTickForRender);
            } finally {
                SimulatedCoastersPlacementScaleContext.pop();
                SimulatedCoastersPlacementScaleContext.remember(1.0D);
            }
        }
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
    private static Vector3dc pocket$scaleProposedBogeySize(final Vector3dc original) {
        final double scale = SimulatedCoastersPlacementScaleContext.current();
        if (!PocketSized.isValidScale(scale) || Math.abs(scale - 1.0D) <= PocketSized.EPSILON) {
            return original;
        }
        return new Vector3d(original).mul(PocketSized.clampScale(scale));
    }
}
