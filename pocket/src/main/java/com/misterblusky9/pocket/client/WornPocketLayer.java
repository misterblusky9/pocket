package com.misterblusky9.pocket.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.misterblusky9.pocket.item.LiveCaseItem;
import com.misterblusky9.pocket.item.PocketCaseItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class WornPocketLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private WornPocketLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    public static void register(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new WornPocketLayer(renderer));
            }
        }
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        renderHead(player, model.head, poseStack, buffers, packedLight);
        renderSlot(player, EquipmentSlot.CHEST, model.body, 9.0D / 16.0D, 2.0F, poseStack, buffers, packedLight);
        renderSlot(player, EquipmentSlot.LEGS, model.body, 21.0D / 16.0D, 2.0F, poseStack, buffers, packedLight);
        renderSlot(player, EquipmentSlot.FEET, model.rightLeg, 13.0D / 16.0D, 2.0F, poseStack, buffers, packedLight);
        renderLiveCase(player, EquipmentSlot.CHEST, model.body, model.head, poseStack, buffers, packedLight);
        renderLiveCase(player, EquipmentSlot.LEGS, model.body, model.head, poseStack, buffers, packedLight);
        renderLiveCase(player, EquipmentSlot.FEET, model.rightLeg, model.head, poseStack, buffers, packedLight);
    }

    public static void renderHead(
            LivingEntity entity,
            ModelPart head,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight
    ) {
        renderSlot(entity, EquipmentSlot.HEAD, head, 0.0D, 4.0F, poseStack, buffers, packedLight);
    }

    private static void renderSlot(
            LivingEntity entity,
            EquipmentSlot slot,
            ModelPart part,
            double y,
            float size,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight
    ) {
        ItemStack stack = entity.getItemBySlot(slot);
        if (!(stack.getItem() instanceof PocketCaseItem) || !PocketCaseItem.isFilled(stack)) return;

        poseStack.pushPose();
        part.translateAndRotate(poseStack);
        poseStack.translate(0.0D, y, 0.0D);
        PocketedSubLevelItemRenderer.renderCraftOnHead(
                stack, poseStack, buffers, packedLight, OverlayTexture.NO_OVERLAY, size);
        poseStack.popPose();
    }

    private static void renderLiveCase(
            AbstractClientPlayer player,
            EquipmentSlot slot,
            ModelPart part,
            ModelPart head,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight
    ) {
        ItemStack stack = player.getItemBySlot(slot);
        if (!(stack.getItem() instanceof LiveCaseItem) || LiveCaseItem.held(stack) == null) return;

        poseStack.pushPose();
        part.translateAndRotate(poseStack);
        poseStack.translate((head.x - part.x) / 16.0F, (head.y - part.y) / 16.0F, (head.z - part.z) / 16.0F);
        CustomHeadLayer.translateToHead(poseStack, false);
        Minecraft.getInstance().getItemRenderer().renderStatic(player, stack, ItemDisplayContext.HEAD, false,
                poseStack, buffers, player.level(), packedLight, OverlayTexture.NO_OVERLAY, player.getId());
        poseStack.popPose();
    }
}
