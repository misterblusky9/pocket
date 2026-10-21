package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.entity.HattersStand;
import com.misterblusky9.pocket.entity.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public class HattersStandRenderer extends LivingEntityRenderer<HattersStand, HattersStandModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "textures/item/hatters_stand.png");
    private static final float PLAYER_SCALE = 0.9375F;
    private static final float NAME_RANGE = 64.0F;
    private static final float CROUCHING_NAME_RANGE = 32.0F;

    public HattersStandRenderer(EntityRendererProvider.Context context) {
        super(context, new HattersStandModel(context.bakeLayer(HattersStandModel.LAYER)), 0.25F);
        addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        addLayer(new RenderLayer<>(this) {
            @Override
            public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, HattersStand stand,
                               float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                               float netHeadYaw, float headPitch) {
                WornPocketLayer.renderHead(stand, getParentModel().getHead(), poseStack, buffers, packedLight);
            }
        });
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.HATTERS_STAND.get(), HattersStandRenderer::new);
    }

    public static void registerLayer(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HattersStandModel.LAYER, HattersStandModel::createLayer);
    }

    @Override
    public ResourceLocation getTextureLocation(HattersStand stand) {
        return TEXTURE;
    }

    @Override
    protected void scale(HattersStand stand, PoseStack poseStack, float partialTick) {
        poseStack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
    }

    @Override
    protected boolean shouldShowName(HattersStand stand) {
        float range = stand.isCrouching() ? CROUCHING_NAME_RANGE : NAME_RANGE;
        return this.entityRenderDispatcher.distanceToSqr(stand) < range * range && stand.isCustomNameVisible();
    }
}
