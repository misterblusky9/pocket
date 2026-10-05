package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.entity.ModEntities;
import com.misterblusky9.pocket.entity.TheMoonPackageEntity;
import com.misterblusky9.pocket.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class TheMoonPackageRenderer extends EntityRenderer<TheMoonPackageEntity> {
    private final ItemRenderer itemRenderer;
    private final ItemStack moonStack;

    public TheMoonPackageRenderer(final EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
        moonStack = new ItemStack(ModItems.THE_MOON.get());
        shadowRadius = 1.0F;
    }

    public static void register(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ModEntities.THE_MOON_PACKAGE.get(),
                TheMoonPackageRenderer::new
        );
    }

    @Override
    public void render(
            final TheMoonPackageEntity entity,
            final float yaw,
            final float partialTick,
            final PoseStack poseStack,
            final MultiBufferSource buffer,
            final int light
    ) {
        poseStack.pushPose();
        poseStack.translate(0.0D, 1.0D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        poseStack.scale(2.0F, 2.0F, 2.0F);

        itemRenderer.renderStatic(
                moonStack,
                ItemDisplayContext.NONE,
                light,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                entity.getId()
        );

        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(final TheMoonPackageEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
