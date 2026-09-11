package com.misterblusky9.pocket.mixin.interaction;

import com.misterblusky9.pocket.interaction.ScaledPlacementGate;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// runs ahead of Sable's canPlace gate, which models every block as 1x1x1 world units
@Mixin(value = BlockPlaceContext.class, priority = 500)
public abstract class PlacementMarginMixin {
    @Shadow protected boolean replaceClicked;

    @Inject(method = "canPlace", at = @At("HEAD"), cancellable = true)
    private void pocket$scaleAwareCrossSubLevelGate(final CallbackInfoReturnable<Boolean> cir) {
        final BlockPlaceContext context = (BlockPlaceContext) (Object) this;

        if (!this.replaceClicked
                && !context.getLevel().getBlockState(context.getClickedPos()).canBeReplaced(context)) {
            cir.setReturnValue(false);
            return;
        }

        final Boolean decision = ScaledPlacementGate.evaluate(context);
        if (decision != null) cir.setReturnValue(decision);
    }
}
