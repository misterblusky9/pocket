package com.misterblusky9.pocket.mixin.simulatedcoasters;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.PocketSized;
import com.simibubi.create.content.trains.track.BezierConnection;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.client.track.AnchorPeerCurvePick", remap = false)
public abstract class AnchorPeerCurvePickScaleMixin {
    @WrapOperation(
            method = "refineAfterCreatePass(Lnet/minecraft/client/Minecraft;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
            ),
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/phys/AABB;clip(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/Optional;"
                    ),
                    to = @At(
                            value = "INVOKE",
                            target = "Ldev/silvergold/simulatedcoasters/client/track/AnchorPeerCurvePick;curvePickMidWorld(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/BlockPos;Lnet/minecraft/client/Minecraft;F)Lnet/minecraft/world/phys/Vec3;"
                    )
            ),
            remap = false,
            require = 1
    )
    private static double pocket$curveDistanceInWorldMetric(
            final Vec3 hit,
            final Vec3 origin,
            final Operation<Double> original,
            @Local(index = 28) final Pose3d anchorPose
    ) {
        final double distanceSq = original.call(hit, origin);
        if (anchorPose == null) return distanceSq;

        final double scale = anchorPose.scale().x();
        if (!PocketSized.isValidScale(scale)) return distanceSq;
        return distanceSq * scale * scale;
    }

    @WrapOperation(
            method = "refineAfterCreatePass(Lnet/minecraft/client/Minecraft;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/silvergold/simulatedcoasters/track/anchor/AnchorPeerFakeTracks;blockPosForCurveHit(Lcom/simibubi/create/content/trains/track/BezierConnection;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/core/BlockPos;"
            ),
            remap = false,
            require = 1
    )
    private static BlockPos pocket$rasterHitInTrackSpace(
            final BezierConnection connection,
            final Vec3 worldHit,
            final Operation<BlockPos> original,
            @Local(argsOnly = true, index = 0) final Minecraft minecraft,
            @Local(index = 2) final float partialTick,
            @Local(index = 23) final BlockPos hostAnchorPos
    ) {
        if (minecraft == null || minecraft.level == null || connection == null || worldHit == null) {
            return original.call(connection, worldHit);
        }

        try {
            final ClientSubLevel host = pocket$clientHost(minecraft, hostAnchorPos);
            if (host == null || host.isRemoved()) return original.call(connection, worldHit);

            final Pose3d pose = (Pose3d) host.renderPose(partialTick);
            if (pose == null) return original.call(connection, worldHit);
            return original.call(connection, pose.transformPositionInverse(worldHit));
        } catch (RuntimeException ignored) {
            return original.call(connection, worldHit);
        }
    }

    private static ClientSubLevel pocket$clientHost(final Minecraft minecraft, final BlockPos pos) {
        if (pos == null) return null;
        final SubLevel subLevel = Sable.HELPER.getContaining(minecraft.level, pos);
        return subLevel instanceof ClientSubLevel client ? client : null;
    }
}
