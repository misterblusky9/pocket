package com.misterblusky9.pocket.mixin.client;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.network.client.SubLevelSnapshotInterpolator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SubLevelSnapshotInterpolator.class, remap = false)
public interface SubLevelSnapshotInterpolatorAccessor {
    @Accessor("runningSnapshot")
    Pose3d pocket$getRunningSnapshot();
}
