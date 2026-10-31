package com.misterblusky9.pocket.mixin.simulated;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pocket.compat.simulated.WeldMergeMigration;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = SimAssemblyHelper.class, remap = false)
public abstract class SlimeWeldDisassemblyMixin {
    @WrapMethod(method = "disassembleSubLevel")
    private static void pocket$preserveHotGlueOnMerge(
            Level level, SubLevel source, BlockPos sourceAnchor, BlockPos destinationAnchor,
            Rotation rotation, boolean playSound, Operation<Void> original) {
        WeldMergeMigration.Move move = WeldMergeMigration.begin(
                level, source, sourceAnchor, destinationAnchor, rotation);
        boolean completed = false;
        try {
            original.call(level, source, sourceAnchor, destinationAnchor, rotation, playSound);
            completed = true;
        } finally {
            WeldMergeMigration.finish(move, completed);
        }
    }
}
