package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.misterblusky9.pocket.client.PocketSizedClient;
import com.misterblusky9.pocket.client.PocketedSubLevelItemRenderer;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PackageRenderer.class, remap = false)
public abstract class PackageEntityCraftRenderMixin {
    private static final double BOX_BASE_Y = 0.5D;

    @ModifyExpressionValue(
            method = "render",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"),
            remap = false
    )
    private Object pocket$ownBoxModel(
            final Object original,
            final PackageEntity entity,
            final float yaw,
            final float partialTick,
            final PoseStack ms,
            final MultiBufferSource buffer,
            final int light
    ) {
        return isPocketed(entity) ? PocketSizedClient.boxModelFor(entity.box) : original;
    }

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void pocket$drawCraftInsideBox(
            final PackageEntity entity,
            final float yaw,
            final float partialTick,
            final PoseStack ms,
            final MultiBufferSource buffer,
            final int light,
            final CallbackInfo ci
    ) {
        if (!isPocketed(entity)) return;

        ms.pushPose();

        ms.translate(0.0D, BOX_BASE_Y, 0.0D);
        ms.mulPose(Axis.YP.rotationDegrees(-yaw - 90.0F));
        PocketedSubLevelItemRenderer.renderCraftInBox(
                entity.box, ms, buffer, light, OverlayTexture.NO_OVERLAY);
        ms.popPose();
    }

    private static boolean isPocketed(final PackageEntity entity) {
        final ItemStack box = entity == null ? ItemStack.EMPTY : entity.box;
        return box != null && !box.isEmpty()
                && box.getItem() instanceof PocketCaseItem
                && PocketCaseItem.isFilled(box);
    }
}
