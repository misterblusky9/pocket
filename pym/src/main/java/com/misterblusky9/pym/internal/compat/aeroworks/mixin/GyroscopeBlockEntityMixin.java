package com.misterblusky9.pym.internal.compat.aeroworks.mixin;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mred231.aeroworks.content.gyroscope.GyroscopeBlockEntity", remap = false)
public abstract class GyroscopeBlockEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"), require = 1)
    private void pym$restoreActor(CallbackInfo ci) {
        BlockEntity gyro = (BlockEntity) (Object) this;
        if (gyro.getLevel() == null || gyro.getLevel().isClientSide || gyro.isRemoved()) return;
        if (gyro.getLevel().getBlockEntity(gyro.getBlockPos()) != gyro) return;
        if (!(Sable.HELPER.getContaining(gyro) instanceof ServerSubLevel subLevel) || subLevel.isRemoved()) return;

        var plot = subLevel.getPlot();
        if (((LevelPlotActorAccessor) plot).pym$getActors().get(gyro.getBlockPos()) != gyro) {
            plot.onBlockChange(gyro.getBlockPos(), gyro.getBlockState());
        }
    }
}
