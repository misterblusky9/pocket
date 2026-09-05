package com.misterblusky9.pocket.mixin.simulated;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pocket.compat.simulated.SimulatedGlueLookupContext;
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = SimAssemblyContraption.class, remap = false)
public abstract class SimAssemblyGlueLookupMixin {
    @WrapMethod(method = "checkAndCacheGlue")
    private boolean pocket$directGlueLookup(
            final LevelAccessor level,
            final BlockPos blockPos,
            final BlockPos offsetDir,
            final Operation<Boolean> original
    ) {
        SimulatedGlueLookupContext.enter();
        try {
            return original.call(level, blockPos, offsetDir);
        } finally {
            SimulatedGlueLookupContext.exit();
        }
    }

    @WrapMethod(method = "addInitialHoneyGlue")
    private static void pocket$directInitialHoneyGlueLookup(
            final Level level,
            final SimAssemblyContraption contraption,
            final BlockPos anchor,
            final BlockPos pos,
            final boolean ignoreEnclosingGlue,
            final Operation<Void> original
    ) {
        SimulatedGlueLookupContext.enter();
        try {
            original.call(level, contraption, anchor, pos, ignoreEnclosingGlue);
        } finally {
            SimulatedGlueLookupContext.exit();
        }
    }
}
