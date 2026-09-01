package com.misterblusky9.pocket.mixin.simulatedcoasters;

import com.misterblusky9.pocket.PocketSized;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.core.Position;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.client.track.OrientedBoxOutlineRenderer", remap = false)
public abstract class RivetOutlineScaleMixin {
    @Unique
    private static float pocket$rivetOutlineScale = 1.0F;

    @Inject(
            method = "show(Lnet/createmod/catnip/outliner/Outliner;Ljava/lang/String;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;DDDI)V",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private static void pocket$captureRivetOutlineScale(
            final Outliner outliner,
            final String key,
            final Vec3 center,
            final Vec3 axisRight,
            final Vec3 axisUp,
            final Vec3 axisForward,
            final double halfRight,
            final double halfUp,
            final double halfForward,
            final int color,
            final CallbackInfo ci
    ) {
        pocket$rivetOutlineScale = 1.0F;
        if (center == null) return;

        if (!(Sable.HELPER.getContainingClient((Position) center) instanceof final ClientSubLevel subLevel)) return;
        if (subLevel.isRemoved()) return;

        final Vector3dc scale = subLevel.renderPose().scale();
        final double value = scale.x();
        if (!Double.isFinite(value) || value <= PocketSized.EPSILON) return;

        pocket$rivetOutlineScale = (float) value;
    }

    @ModifyArg(
            method = "show(Lnet/createmod/catnip/outliner/Outliner;Ljava/lang/String;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;DDDI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/createmod/catnip/outliner/Outline$OutlineParams;lineWidth(F)Lnet/createmod/catnip/outliner/Outline$OutlineParams;"
            ),
            index = 0,
            remap = false,
            require = 0
    )
    private static float pocket$scaleRivetOutlineWidth(final float width) {
        return width * pocket$rivetOutlineScale;
    }

    @Inject(
            method = "show(Lnet/createmod/catnip/outliner/Outliner;Ljava/lang/String;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;DDDI)V",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void pocket$clearRivetOutlineScale(
            final Outliner outliner,
            final String key,
            final Vec3 center,
            final Vec3 axisRight,
            final Vec3 axisUp,
            final Vec3 axisForward,
            final double halfRight,
            final double halfUp,
            final double halfForward,
            final int color,
            final CallbackInfo ci
    ) {
        pocket$rivetOutlineScale = 1.0F;
    }
}
