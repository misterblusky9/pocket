package com.misterblusky9.pym.internal.mixin.client;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.network.client.SubLevelSnapshotInterpolator;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.scale.ScaleMotion;
import com.misterblusky9.pym.internal.scale.ScaleState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientSubLevel.class, remap = false)
public abstract class ClientSubLevelMixin {
    @Unique
    private static final double pym$LIGHT_PROBE_EPSILON = 1.0D / 32.0D;

    @Unique
    private final Pose3d pym$lightPose = new Pose3d();

    @Unique
    private final BoundingBox3d pym$lightBounds = new BoundingBox3d();

    @Unique
    private boolean pym$snapThisTick;

    @Inject(
            method = "computeSubLevelSkyLight",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void pym$sampleScaledSkyLightFromPhysicalBounds(
            Pose3dc pose,
            CallbackInfoReturnable<Integer> cir
    ) {
        ClientSubLevel self = (ClientSubLevel) (Object) this;
        if (!ScaleState.isScaled(self)) return;

        double scale = ScaleState.getClientScale(self);
        this.pym$lightPose.set(pose);
        this.pym$lightPose.scale().set(scale, scale, scale);

        BoundingBox3ic plot = self.getPlot().getBoundingBox();
        BoundingBox3dc box = this.pym$lightBounds
                .set(
                        plot.minX(), plot.minY(), plot.minZ(),
                        plot.maxX() + 1.0D, plot.maxY() + 1.0D, plot.maxZ() + 1.0D
                )
                .transform(this.pym$lightPose);

        double minX = box.minX();
        double minY = box.minY();
        double minZ = box.minZ();
        double maxX = box.maxX();
        double maxY = box.maxY();
        double maxZ = box.maxZ();

        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                || maxX < minX || maxY < minY || maxZ < minZ) {
            return;
        }

        ClientLevel level = self.getLevel();
        double epsilon = pym$LIGHT_PROBE_EPSILON;
        double centerX = (minX + maxX) * 0.5D;
        double centerY = (minY + maxY) * 0.5D;
        double centerZ = (minZ + maxZ) * 0.5D;

        double outsideMinX = minX - epsilon;
        double outsideMinY = minY - epsilon;
        double outsideMinZ = minZ - epsilon;
        double outsideMaxX = maxX + epsilon;
        double outsideMaxY = maxY + epsilon;
        double outsideMaxZ = maxZ + epsilon;

        int skyLight = 0;

        skyLight = pym$maxSkyLight(level, skyLight, centerX, outsideMaxY, centerZ);
        skyLight = pym$maxSkyLight(level, skyLight, centerX, outsideMinY, centerZ);
        skyLight = pym$maxSkyLight(level, skyLight, outsideMinX, centerY, centerZ);
        skyLight = pym$maxSkyLight(level, skyLight, outsideMaxX, centerY, centerZ);
        skyLight = pym$maxSkyLight(level, skyLight, centerX, centerY, outsideMinZ);
        skyLight = pym$maxSkyLight(level, skyLight, centerX, centerY, outsideMaxZ);

        skyLight = pym$maxSkyLight(level, skyLight, outsideMinX, outsideMinY, outsideMinZ);
        skyLight = pym$maxSkyLight(level, skyLight, outsideMinX, outsideMinY, outsideMaxZ);
        skyLight = pym$maxSkyLight(level, skyLight, outsideMinX, outsideMaxY, outsideMinZ);
        skyLight = pym$maxSkyLight(level, skyLight, outsideMinX, outsideMaxY, outsideMaxZ);
        skyLight = pym$maxSkyLight(level, skyLight, outsideMaxX, outsideMinY, outsideMinZ);
        skyLight = pym$maxSkyLight(level, skyLight, outsideMaxX, outsideMinY, outsideMaxZ);
        skyLight = pym$maxSkyLight(level, skyLight, outsideMaxX, outsideMaxY, outsideMinZ);
        skyLight = pym$maxSkyLight(level, skyLight, outsideMaxX, outsideMaxY, outsideMaxZ);

        cir.setReturnValue(skyLight);
    }

    @Unique
    private static int pym$maxSkyLight(
            ClientLevel level,
            int current,
            double x,
            double y,
            double z
    ) {
        if (current >= 15) return 15;
        return Math.max(
                current,
                level.getBrightness(LightLayer.SKY, BlockPos.containing(x, y, z))
        );
    }

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void pym$resetDiscontinuousPlacementInterpolation(CallbackInfo ci) {
        ClientSubLevel self = (ClientSubLevel) (Object) this;
        this.pym$snapThisTick =
                ScaleState.consumeClientInterpolationSnap(self.getUniqueId());

        if (!this.pym$snapThisTick) return;

        SubLevelSnapshotInterpolator interpolator = self.getInterpolator();

        Pose3dc target = interpolator.buffer.isEmpty()
                ? self.logicalPose()
                : interpolator.buffer.getLast().pose();

        double scale = ScaleState.getClientScale(self);
        boolean validScale = ScaleBounds.isValid(scale);

        Pose3d runningSnapshot =
                ((SubLevelSnapshotInterpolatorAccessor) interpolator)
                        .pym$getRunningSnapshot();

        runningSnapshot.set(target);
        if (validScale) {
            runningSnapshot.scale().set(scale, scale, scale);
        }

        self.logicalPose().set(target);
        if (validScale) {
            self.logicalPose().scale().set(scale, scale, scale);
        }

        interpolator.buffer.clear();
        self.updateLastPose();
        self.forceUpdateBounds();
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/network/client/SubLevelSnapshotInterpolator;tick(D)V",
                    shift = At.Shift.AFTER
            ),
            remap = false
    )
    private void pym$preserveScaleThroughSableInterpolation(CallbackInfo ci) {
        ClientSubLevel self = (ClientSubLevel) (Object) this;
        if (!ScaleState.hasClientSnapshot(self.getUniqueId())) return;

        double scale = ScaleState.getClientScale(self);
        if (!ScaleBounds.isValid(scale)) return;

        ((SubLevelSnapshotInterpolatorAccessor) self.getInterpolator())
                .pym$getRunningSnapshot()
                .scale()
                .set(scale, scale, scale);
    }

    @Inject(method = "tick", at = @At("RETURN"), remap = false)
    private void pym$restoreNetworkedScaleAfterSableSnapshot(CallbackInfo ci) {
        ClientSubLevel self = (ClientSubLevel) (Object) this;
        ScaleMotion.enforceClientScale(self);

        if (this.pym$snapThisTick) {
            self.updateLastPose();
            self.forceUpdateBounds();
            this.pym$snapThisTick = false;
        }
    }
}
