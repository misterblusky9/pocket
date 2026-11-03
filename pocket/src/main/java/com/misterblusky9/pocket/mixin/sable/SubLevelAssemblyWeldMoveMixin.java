package com.misterblusky9.pocket.mixin.sable;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import com.misterblusky9.pocket.compat.simulated.WeldMergeMigration;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class SubLevelAssemblyWeldMoveMixin {
    @WrapMethod(method = "moveBlocks")
    private static void pocket$moveWelds(
            ServerLevel level,
            SubLevelAssemblyHelper.AssemblyTransform transform,
            Iterable<BlockPos> blocks,
            Operation<Void> original
    ) {
        List<BlockPos> movable = new ArrayList<>();
        for (BlockPos pos : blocks) {
            if (!level.getBlockState(pos).is(ModBlocks.POCKET_DUMMY.get())) movable.add(pos);
        }
        if (movable.isEmpty()) return;
        CrossScaleWelds.beginAssemblyMove();
        try {
            original.call(level, transform, movable);
            if (!WeldMergeMigration.isMoving(level)) {
                CrossScaleWelds.remapAssemblyAnchors(level, transform, movable);
                CrossScaleWelds.refreshLoaded(transform.getLevel());
                if (transform.getLevel() != level) CrossScaleWelds.refreshLoaded(level);
            }
        } finally {
            CrossScaleWelds.endAssemblyMove();
        }
    }
}
