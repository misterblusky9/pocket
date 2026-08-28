package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.PocketSized;
import com.simibubi.create.content.trains.track.TrackPlacement;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = TrackPlacement.class, remap = false)
public abstract class TrackPlacementLineScaleMixin {
    @Shadow
    private static BlockPos hoveringPos;

    @ModifyArg(
            method = "clientTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/createmod/catnip/outliner/Outline$OutlineParams;lineWidth(F)Lnet/createmod/catnip/outliner/Outline$OutlineParams;"
            ),
            index = 0,
            require = 1
    )
    private static float pocket$scaleCurveLineWidth(final float width) {
        return width * pocket$trackPlacementScale();
    }

    @ModifyArg(
            method = "line",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/createmod/catnip/outliner/Outline$OutlineParams;lineWidth(F)Lnet/createmod/catnip/outliner/Outline$OutlineParams;"
            ),
            index = 0,
            require = 1
    )
    private static float pocket$scaleStraightLineWidth(final float width) {
        return width * pocket$trackPlacementScale();
    }

    @Unique
    private static float pocket$trackPlacementScale() {
        if (hoveringPos == null) return 1.0F;

        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return 1.0F;

        if (!(Sable.HELPER.getContaining(level, hoveringPos) instanceof final ClientSubLevel subLevel)) return 1.0F;
        if (subLevel.isRemoved()) return 1.0F;

        final Vector3dc scale = subLevel.renderPose().scale();
        final double value = scale.x();
        if (!Double.isFinite(value) || value <= PocketSized.EPSILON) return 1.0F;

        return (float) value;
    }
}
