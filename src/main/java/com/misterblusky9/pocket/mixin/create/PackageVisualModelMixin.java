package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.misterblusky9.pocket.client.PocketSizedClient;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PackageVisual.class, remap = false)
public abstract class PackageVisualModelMixin {
    @ModifyExpressionValue(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"),
            remap = false
    )
    private Object pocket$ownBoxModel(
            final Object original,
            final VisualizationContext ctx,
            final PackageEntity entity,
            final float partialTick
    ) {
        final ItemStack box = entity == null ? ItemStack.EMPTY : entity.box;
        final boolean pocketed = box != null && !box.isEmpty()
                && box.getItem() instanceof PocketCaseItem
                && PocketCaseItem.isFilled(box);
        return pocketed ? PocketSizedClient.boxModelFor(box) : original;
    }
}
