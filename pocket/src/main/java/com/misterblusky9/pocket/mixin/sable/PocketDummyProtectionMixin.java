package com.misterblusky9.pocket.mixin.sable;

import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pocket.build.PocketDummyBlock;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class PocketDummyProtectionMixin {
    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"), cancellable = true)
    private void pocket$protectDummy(BlockPos pos, BlockState replacement, boolean moving,
                                      CallbackInfoReturnable<BlockState> cir) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        if (PocketDummyBlock.editing() || !(chunk.getLevel() instanceof ServerLevel level)) return;
        BlockState previous = chunk.getBlockState(pos);
        if (previous.is(ModBlocks.POCKET_DUMMY.get()) && previous != replacement
                && BuildSites.isAttached(Sable.HELPER.getContaining(level, pos)))
            cir.setReturnValue(previous);
    }
}
