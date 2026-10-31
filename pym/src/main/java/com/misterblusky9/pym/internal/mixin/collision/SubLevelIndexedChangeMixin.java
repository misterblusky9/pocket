package com.misterblusky9.pym.internal.mixin.collision;

import com.misterblusky9.pym.internal.collision.SubLevelIndexedCollision;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class SubLevelIndexedChangeMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void pym$markIndexedSection(BlockPos pos, BlockState state, boolean moved,
                                        CallbackInfoReturnable<BlockState> info) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        BlockState old = info.getReturnValue();
        if (old != null) SubLevelIndexedCollision.changed(chunk.getLevel(), pos, old, state);
    }

    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void pym$forgetIndexedChunk(CallbackInfo info) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        SubLevelIndexedCollision.reloaded(chunk.getLevel(), chunk.getPos());
    }
}
