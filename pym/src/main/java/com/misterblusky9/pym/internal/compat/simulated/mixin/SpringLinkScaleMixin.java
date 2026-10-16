package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.api.Pym;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SpringBlockEntity.class, remap = false)
public abstract class SpringLinkScaleMixin {
    @Shadow private UUID partnerSubLevel;

    @WrapOperation(method = "sable$physicsTick", at = @At(value = "INVOKE", target = "Ldev/simulated_team/simulated/content/blocks/spring/SpringBlockEntity;getSnappingDistance()D"), remap = false)
    private double pym$scaleSnap(SpringBlockEntity spring, Operation<Double> original,
                                 @Local(argsOnly = true) ServerSubLevel subLevel) {
        SubLevel partner = this.partnerSubLevel != null
                ? SubLevelContainer.getContainer(subLevel.getLevel()).getSubLevel(this.partnerSubLevel) : null;
        return original.call(spring) * Pym.scale().linkFactor(subLevel, partner);
    }
}
