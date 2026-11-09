package com.misterblusky9.pym.internal.mixin.sable;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.misterblusky9.pym.api.Hosted;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ServerSubLevel.class, remap = false)
public abstract class HostedNoSplitMixin {
    @WrapWithCondition(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/sublevel/plot/heat/SubLevelHeatMapManager;tick()V"),
            remap = false,
            require = 1
    )
    private boolean pym$keepWhole(SubLevelHeatMapManager manager) {
        return !Hosted.isHosted((ServerSubLevel) (Object) this);
    }
}
