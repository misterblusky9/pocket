package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.client.SubspaceHarnessClient;
import com.simibubi.create.content.equipment.armor.BacktankRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BacktankRenderer.class, remap = false)
public abstract class BacktankRendererPartialsMixin {
    @Inject(method = "getCogsModel", at = @At("HEAD"), cancellable = true, require = 1)
    private static void pocket$harnessCogs(final BlockState state, final CallbackInfoReturnable<PartialModel> cir) {
        if (state.is(ModBlocks.SUBSPACE_HARNESS.get())) {
            cir.setReturnValue(SubspaceHarnessClient.COGS);
        }
    }

    @Inject(method = "getShaftModel", at = @At("HEAD"), cancellable = true, require = 1)
    private static void pocket$harnessShaft(final BlockState state, final CallbackInfoReturnable<PartialModel> cir) {
        if (state.is(ModBlocks.SUBSPACE_HARNESS.get())) {
            cir.setReturnValue(SubspaceHarnessClient.SHAFT);
        }
    }
}
