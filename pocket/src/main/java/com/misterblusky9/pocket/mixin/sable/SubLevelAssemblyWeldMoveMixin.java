package com.misterblusky9.pocket.mixin.sable;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import com.misterblusky9.pocket.compat.simulated.WeldMergeMigration;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class SubLevelAssemblyWeldMoveMixin {
    @WrapMethod(method = "moveBlocks")
    private static void pocket$moveWelds(
            ServerLevel level,
            SubLevelAssemblyHelper.AssemblyTransform transform,
            Iterable<BlockPos> blocks,
            Operation<Void> original
    ) {
        CrossScaleWelds.beginAssemblyMove();
        try {
            original.call(level, transform, blocks);
            if (!WeldMergeMigration.isMoving(level)) {
                CrossScaleWelds.refreshLoaded(transform.getLevel());
                if (transform.getLevel() != level) CrossScaleWelds.refreshLoaded(level);
            }
        } finally {
            CrossScaleWelds.endAssemblyMove();
        }
    }
}
