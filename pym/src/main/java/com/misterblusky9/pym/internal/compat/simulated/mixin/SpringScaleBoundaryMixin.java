package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.misterblusky9.pym.internal.compat.simulated.SimulatedSpringScaleBoundary;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SpringBlockEntity.class, remap = false)
public abstract class SpringScaleBoundaryMixin {
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void pym$snapAcrossScales(CallbackInfo ci) {
        SpringBlockEntity self = (SpringBlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel level) || self.isRemoved()) return;
        if (!SimulatedSpringScaleBoundary.crossesBoundary(self)) return;
        level.destroyBlock(self.getBlockPos(), true);
    }
}
