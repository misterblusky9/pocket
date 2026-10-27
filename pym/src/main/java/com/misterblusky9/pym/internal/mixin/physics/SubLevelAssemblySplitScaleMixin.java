package com.misterblusky9.pym.internal.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pym.internal.scale.SplitInheritance;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class SubLevelAssemblySplitScaleMixin {
    @WrapMethod(method = "assembleBlocks", remap = false)
    private static ServerSubLevel pym$adoptOrphanedSplit(
            ServerLevel level,
            BlockPos anchor,
            Iterable<BlockPos> blocks,
            BoundingBox3ic bounds,
            Operation<ServerSubLevel> original
    ) {
        SplitInheritance.Source source = SplitInheritance.capture(level, anchor);
        ServerSubLevel child = original.call(level, anchor, blocks, bounds);
        SplitInheritance.adoptOrphan(source, child);
        return child;
    }
}
