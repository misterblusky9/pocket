package com.misterblusky9.pocket.mixin.simulatedcoasters;

import com.misterblusky9.pocket.PocketSized;
import com.simibubi.create.content.trains.track.BezierConnection;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.CoasterTrackPlacement", remap = false)
public abstract class CoasterTrackPlacementLineScaleMixin {
    @Shadow
    static BlockPos hoveringPos;

    @Unique
    private static float pocket$curvePreviewScale = 1.0F;

    @Inject(
            method = "drawCurvePreviewInner(Lnet/minecraft/world/level/Level;"
                    + "Lcom/simibubi/create/content/trains/track/BezierConnection;"
                    + "Ljava/lang/String;IDIFFZ)I",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private static void pocket$captureCurvePreviewScale(
            final Level level,
            final BezierConnection connection,
            final String key,
            final int color,
            final double yOffset,
            final int previousLineCount,
            final float animation,
            final float lineWidth,
            final boolean drawOpenEnds,
            final CallbackInfoReturnable<Integer> cir
    ) {
        pocket$curvePreviewScale = pocket$scaleForConnection(level, connection);
    }

    @ModifyArg(
            method = "drawCurvePreviewInner(Lnet/minecraft/world/level/Level;"
                    + "Lcom/simibubi/create/content/trains/track/BezierConnection;"
                    + "Ljava/lang/String;IDIFFZ)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/createmod/catnip/outliner/Outline$OutlineParams;"
                            + "lineWidth(F)Lnet/createmod/catnip/outliner/Outline$OutlineParams;"
            ),
            index = 0,
            remap = false,
            require = 0
    )
    private static float pocket$scaleMainCurvePreviewWidth(final float width) {
        return width * pocket$curvePreviewScale;
    }

    @ModifyArg(
            method = "drawOutlineRailLine(Lnet/minecraft/world/level/Level;Ljava/lang/String;IZIFF"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;DLnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/core/BlockPos;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/createmod/catnip/outliner/Outline$OutlineParams;"
                            + "lineWidth(F)Lnet/createmod/catnip/outliner/Outline$OutlineParams;"
            ),
            index = 0,
            remap = false,
            require = 0
    )
    private static float pocket$scaleOpenEndCurvePreviewWidth(final float width) {
        return width * pocket$curvePreviewScale;
    }

    @Inject(
            method = "drawCurvePreviewInner(Lnet/minecraft/world/level/Level;"
                    + "Lcom/simibubi/create/content/trains/track/BezierConnection;"
                    + "Ljava/lang/String;IDIFFZ)I",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void pocket$clearCurvePreviewScale(
            final Level level,
            final BezierConnection connection,
            final String key,
            final int color,
            final double yOffset,
            final int previousLineCount,
            final float animation,
            final float lineWidth,
            final boolean drawOpenEnds,
            final CallbackInfoReturnable<Integer> cir
    ) {
        pocket$curvePreviewScale = 1.0F;
    }

    @ModifyArg(
            method = "line(ILnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/createmod/catnip/outliner/Outline$OutlineParams;"
                            + "lineWidth(F)Lnet/createmod/catnip/outliner/Outline$OutlineParams;"
            ),
            index = 0,
            remap = false,
            require = 0
    )
    private static float pocket$scaleStraightGuideWidth(final float width) {
        return width * pocket$scaleForHoveringPos();
    }

    @Unique
    private static float pocket$scaleForConnection(final Level level, final BezierConnection connection) {
        if (level == null || connection == null || connection.bePositions == null) return 1.0F;

        final BlockPos first = connection.bePositions.getFirst();
        final float firstScale = pocket$scaleForPos(level, first);
        if (firstScale != 1.0F) return firstScale;

        final BlockPos second = connection.bePositions.getSecond();
        return pocket$scaleForPos(level, second);
    }

    @Unique
    private static float pocket$scaleForHoveringPos() {
        if (hoveringPos == null) return 1.0F;

        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return 1.0F;

        return pocket$scaleForPos(level, hoveringPos);
    }

    @Unique
    private static float pocket$scaleForPos(final Level level, final BlockPos pos) {
        if (level == null || pos == null) return 1.0F;
        if (!(Sable.HELPER.getContaining(level, pos) instanceof final ClientSubLevel subLevel)) return 1.0F;
        if (subLevel.isRemoved()) return 1.0F;

        final Vector3dc scale = subLevel.renderPose().scale();
        final double value = scale.x();
        if (!Double.isFinite(value) || value <= PocketSized.EPSILON) return 1.0F;

        return (float) value;
    }
}
