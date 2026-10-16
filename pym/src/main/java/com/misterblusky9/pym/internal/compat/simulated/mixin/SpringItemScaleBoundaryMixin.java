package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.internal.compat.simulated.SimulatedSpringScaleBoundary;
import dev.simulated_team.simulated.content.items.spring.SpringItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SpringItemHandler.class, remap = false)
public abstract class SpringItemScaleBoundaryMixin {
    @Shadow public BlockPos linkPos;

    @WrapOperation(method = "onUse", at = @At(value = "INVOKE", target =
            "Ldev/simulated_team/simulated/content/items/spring/SpringItemHandler;testPlacementAndSendError(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z"),
            remap = false)
    private boolean pym$refuseCrossScale(SpringItemHandler handler, Level level, BlockPos relative,
                                         BlockPos pos, Direction dir, Operation<Boolean> original) {
        if (!original.call(handler, level, relative, pos, dir)) return false;
        return this.linkPos == null || SimulatedSpringScaleBoundary.canConnect(level, this.linkPos, pos);
    }

    @WrapOperation(method = "clientTick", at = @At(value = "INVOKE", target =
            "Ldev/simulated_team/simulated/content/items/spring/SpringItemHandler;testExceedsRange(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Z"),
            remap = false)
    private boolean pym$previewCrossScale(SpringItemHandler handler, Level level, BlockPos child,
                                          BlockPos parent, Operation<Boolean> original) {
        return original.call(handler, level, child, parent)
                || !SimulatedSpringScaleBoundary.canConnect(level, child, parent);
    }
}
