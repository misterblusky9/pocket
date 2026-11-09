package com.misterblusky9.pym.internal.mixin.sable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.misterblusky9.pym.api.Hosted;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(value = SubLevelHoldingChunkMap.class, remap = false)
public abstract class HostedStorageMixin {
    @ModifyExpressionValue(
            method = "saveAll",
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;getAllSubLevels()Ljava/util/List;"),
            remap = false
    )
    private List<ServerSubLevel> pym$neverSaveHosted(List<ServerSubLevel> original) {
        return Hosted.visible(original);
    }
}
