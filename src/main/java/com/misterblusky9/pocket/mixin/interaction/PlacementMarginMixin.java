package com.misterblusky9.pocket.mixin.interaction;

import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockPlaceContext.class, priority = 500)
public abstract class PlacementMarginMixin {
    @Inject(method = "canPlace", at = @At("HEAD"), cancellable = true)
    private void pocket$skipFullSizeMarginForCompressedCraft(final CallbackInfoReturnable<Boolean> cir) {
        final BlockPlaceContext context = (BlockPlaceContext) (Object) this;

        final Level level = context.getLevel();
        final BlockPos clicked = context.getClickedPos();

        final SubLevel subLevel = Sable.HELPER.getContaining(level, clicked);
        if (subLevel == null || !ScaleState.isScaled(subLevel)) return;

        cir.setReturnValue(level.getBlockState(clicked).canBeReplaced(context));
    }
}
