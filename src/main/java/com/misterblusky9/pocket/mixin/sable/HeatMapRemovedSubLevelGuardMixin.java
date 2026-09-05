package com.misterblusky9.pocket.mixin.sable;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelHeatMapManager.class, remap = false)
public abstract class HeatMapRemovedSubLevelGuardMixin {
    @Unique
    private static final Logger POCKET$LOGGER = LogUtils.getLogger();

    @Shadow
    @Final
    private ServerSubLevel subLevel;

    @Unique
    private boolean pocket$reported;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void pocket$skipRemovedSubLevelHeatMap(final CallbackInfo ci) {
        if (subLevel == null || !subLevel.isRemoved()) {
            return;
        }

        if (!pocket$reported) {
            pocket$reported = true;
            POCKET$LOGGER.warn(
                    "[PocketSable] Dropped heat-map work for removed sub-level uuid={} plot={};"
                            + " this is the state that crashes SubLevelHeatMapManager#split",
                    subLevel.getUniqueId(),
                    subLevel.getPlot() == null ? "none" : subLevel.getPlot().getBoundingBox()
            );
        }
        ci.cancel();
    }
}
