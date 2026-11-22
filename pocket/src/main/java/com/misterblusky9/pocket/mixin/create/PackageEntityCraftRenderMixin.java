package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.client.PocketSizedClient;
import com.misterblusky9.pocket.client.CreativeCasePortal;
import com.misterblusky9.pocket.client.PocketedSubLevelItemRenderer;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.item.PocketContainer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PackageRenderer.class, remap = false)
public abstract class PackageEntityCraftRenderMixin {
    @Unique
    private static final double BOX_BASE_Y = 0.5D;

    @ModifyExpressionValue(
            method = "render",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"),
            remap = false
    )
    private Object pocket$ownBoxModel(
            Object original,
            PackageEntity entity,
            float yaw,
            float partialTick,
            PoseStack ms,
            MultiBufferSource buffer,
            int light
    ) {
        return isPocketed(entity) ? PocketSizedClient.boxModelFor(entity.box) : original;
    }


    // The vanilla Create package renderer normally draws every baked quad.
    // For creative cases we split that *same* model into shell and shader quads
    // instead. No second shell JSON or model is necessary.
    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE", target =
                    "Lcom/simibubi/create/content/logistics/box/PackageRenderer;" +
                    "renderBox(Lnet/minecraft/world/entity/Entity;FLcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/client/renderer/MultiBufferSource;ILdev/engine_room/flywheel/lib/model/baked/PartialModel;)V"),
            remap = false
    )
    private void pocket$renderCreativeCaseFromOneModel(
            Entity entity, float yaw, PoseStack ms, MultiBufferSource buffer, int light,
            PartialModel model, Operation<Void> original) {
        if (!(entity instanceof PackageEntity box)
                || PocketContainer.of(box.box) != PocketContainer.CREATIVE_POCKET_CASE) {
            original.call(entity, yaw, ms, buffer, light, model);
            return;
        }
        ms.pushPose();
        ms.translate(0.0D, BOX_BASE_Y - CreativeCasePortal.GROUND_DROP, 0.0D);
        ms.mulPose(Axis.YP.rotationDegrees(-yaw - 90.0F));
        CreativeCasePortal.renderShell(box.box, ms, buffer, light, OverlayTexture.NO_OVERLAY);
        CreativeCasePortal.renderPortal(ms, buffer);
        ms.popPose();
    }

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void pocket$drawCraftInsideBox(
            PackageEntity entity,
            float yaw,
            float partialTick,
            PoseStack ms,
            MultiBufferSource buffer,
            int light,
            CallbackInfo ci
    ) {
        boolean filled = isPocketed(entity);
        boolean creative = entity != null && entity.box != null && !entity.box.isEmpty()
                && PocketContainer.of(entity.box) == PocketContainer.CREATIVE_POCKET_CASE;
        if (!creative && !filled) return;

        ms.pushPose();
        ms.translate(0.0D, creative ? BOX_BASE_Y - CreativeCasePortal.GROUND_DROP : BOX_BASE_Y, 0.0D);
        ms.mulPose(Axis.YP.rotationDegrees(-yaw - 90.0F));

        // Create still calls PackageRenderer.render() under Flywheel, but skips
        // renderBox(). The previous working portal pass was at render() TAIL.
        // Keep it here; the renderBox wrapper already draws it for fallback.
        if (creative && VisualizationManager.supportsVisualization(entity.level())) {
            CreativeCasePortal.renderPortal(ms, buffer);
        }
        if (filled) {
            PocketedSubLevelItemRenderer.renderCraftInBox(
                    entity.box, ms, buffer, light, OverlayTexture.NO_OVERLAY);
        }
        ms.popPose();
    }

    private static boolean isPocketed(PackageEntity entity) {
        ItemStack box = entity == null ? ItemStack.EMPTY : entity.box;
        return box != null && !box.isEmpty()
                && box.getItem() instanceof PocketCaseItem
                && PocketCaseItem.isFilled(box);
    }
}
