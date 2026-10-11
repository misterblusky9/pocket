package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.client.CopycatFacadeFrames;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class CopycatFacadeReachMixin {
    @Inject(method = "blockChanged", at = @At("TAIL"))
    private void pocket$refreshFacadeConnections(
            final BlockGetter level,
            final BlockPos pos,
            final BlockState oldState,
            final BlockState newState,
            final int flags,
            final CallbackInfo ci
    ) {
        CopycatFacadeFrames.blockChanged(pos);
    }
}
