package com.misterblusky9.pocket.mixin.physics;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import com.misterblusky9.pocket.scale.ScaleController;
import com.misterblusky9.pocket.scale.ScaleState;
import com.misterblusky9.pocket.scale.SubLevelParentage;
import com.misterblusky9.pocket.physics.PlotShapeCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerSubLevel.class, remap = false)
public abstract class ServerSubLevelSplitScaleMixin {
    @Inject(method = "setSplitFrom", at = @At("HEAD"), remap = false)
    private void pocket$inheritParentScale(
            final ServerSubLevel parent,
            final Pose3d originalPose,
            final CallbackInfo ci
    ) {
        final double scale = ScaleState.getServerScale(parent);

        PlotShapeCache.invalidate(parent);

        originalPose.scale().set(scale, scale, scale);

        final ServerSubLevel child = (ServerSubLevel) (Object) this;

        ScaleController.adoptSplitScale(child, scale);
        PlotShapeCache.invalidate(child);

        SubLevelParentage.record(child, parent);
    }
}
