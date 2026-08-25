package com.misterblusky9.pocket.mixin.simulated;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.PocketSized;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;

@Mixin(targets = "dev.simulated_team.simulated.content.blocks.rope.strand.client.RopeStrandRenderer", remap = false)
public abstract class RopeStrandScaleMixin {
    @Unique private static double pocket$ownerScale = 1.0D;
    @Unique private static double pocket$startScale = 1.0D;
    @Unique private static double pocket$endScale = 1.0D;
    @Unique private static int pocket$segment;
    @Unique private static int pocket$segments;

    @Unique private static double pocket$radial = 1.0D;

    @Unique private static Method pocket$positionAccessor;
    @Unique private static boolean pocket$positionAccessorFailed;

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0),
            remap = false
    )
    private static void pocket$beginStrand(
            final PoseStack poseStack,
            @Local(argsOnly = true) final float partialTick,
            @Local final Level level,
            @Local final Pose3dc containingPose,
            @Local final ObjectArrayList<?> renderPoints
    ) {
        poseStack.pushPose();

        pocket$segment = 0;
        pocket$segments = renderPoints == null ? 0 : Math.max(0, renderPoints.size() - 1);
        pocket$radial = 1.0D;

        pocket$ownerScale = pocket$uniformScale(containingPose);
        pocket$startScale = pocket$ownerScale;

        final Vector3d farEnd = pocket$lastPointPosition(renderPoints);

        pocket$endScale = farEnd == null
                ? pocket$startScale
                : pocket$scaleAt(level, farEnd, partialTick);
    }

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V"),
            remap = false
    )
    private static void pocket$taperSegment(final PoseStack poseStack, final Quaternionf orientation) {
        poseStack.mulPose(orientation);

        final double t = pocket$segments <= 1
                ? 0.0D
                : (double) pocket$segment / (double) (pocket$segments - 1);
        final double target = pocket$startScale + (pocket$endScale - pocket$startScale) * t;

        pocket$radial = pocket$ownerScale <= 0.0D ? 1.0D : target / pocket$ownerScale;
        pocket$segment++;

        if (Math.abs(pocket$radial - 1.0D) <= PocketSized.EPSILON) return;
        final float r = (float) pocket$radial;
        poseStack.scale(r, r, r);
    }

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"),
            remap = false
    )
    private static void pocket$segmentLength(
            final PoseStack poseStack,
            final float x,
            final float y,
            final float z
    ) {
        final double divisor = pocket$ownerScale * pocket$radial;
        if (!(divisor > 0.0D) || !Double.isFinite(divisor)) {
            poseStack.scale(x, y, z);
            return;
        }
        poseStack.scale(x, (float) (y / divisor), z);
    }

    @Unique
    private static double pocket$uniformScale(final Pose3dc pose) {
        if (pose == null) return 1.0D;
        final var scale = pose.scale();
        if (Math.abs(scale.x() - scale.y()) > PocketSized.EPSILON
                || Math.abs(scale.x() - scale.z()) > PocketSized.EPSILON) {
            return 1.0D;
        }
        final double uniform = scale.x();
        return Double.isFinite(uniform) && uniform > 0.0D ? uniform : 1.0D;
    }

    @Unique
    private static double pocket$scaleAt(final Level level, final Vector3d worldPoint, final float partialTick) {
        if (level == null || worldPoint == null) return 1.0D;

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return 1.0D;

        final BoundingBox3dc point = new BoundingBox3d(
                worldPoint.x, worldPoint.y, worldPoint.z,
                worldPoint.x, worldPoint.y, worldPoint.z);

        double best = 1.0D;
        double bestVolume = Double.MAX_VALUE;

        for (final SubLevel candidate : container.queryIntersecting(point)) {
            if (!(candidate instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) continue;

            final BoundingBox3dc bounds = subLevel.boundingBox();
            if (bounds == null) continue;

            final double volume = (bounds.maxX() - bounds.minX())
                    * (bounds.maxY() - bounds.minY())
                    * (bounds.maxZ() - bounds.minZ());
            if (volume >= bestVolume) continue;

            bestVolume = volume;
            best = pocket$uniformScale(subLevel.renderPose(partialTick));
        }

        return best;
    }

    @Unique
    private static Vector3d pocket$lastPointPosition(final ObjectArrayList<?> renderPoints) {
        if (pocket$positionAccessorFailed || renderPoints == null || renderPoints.isEmpty()) return null;

        final Object last = renderPoints.get(renderPoints.size() - 1);
        if (last == null) return null;

        try {
            Method accessor = pocket$positionAccessor;
            if (accessor == null || !accessor.getDeclaringClass().isInstance(last)) {
                accessor = last.getClass().getMethod("position");
                accessor.setAccessible(true);
                pocket$positionAccessor = accessor;
            }
            final Object value = accessor.invoke(last);

            return value instanceof final Vector3d position ? new Vector3d(position) : null;
        } catch (final ReflectiveOperationException | RuntimeException ex) {
            pocket$positionAccessorFailed = true;
            return null;
        }
    }
}
