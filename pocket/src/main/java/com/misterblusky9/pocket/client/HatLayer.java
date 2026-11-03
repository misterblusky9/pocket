package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pym.api.client.Portrait;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;

public final class HatLayer<T extends LivingEntity, M extends EntityModel<T> & HeadedModel> extends RenderLayer<T, M> {
    private static final float SCALE = (float) BuildSites.SCALE;

    public HatLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ClientSubLevel mirror = HatRendering.worn(entity);
        if (mirror == null) return;
        pose.pushPose();
        if (entity.isBaby() && !(entity instanceof Villager)) {
            pose.translate(0.0F, 0.03125F, 0.0F);
            pose.scale(0.7F, 0.7F, 0.7F);
            pose.translate(0.0F, 1.0F, 0.0F);
        }
        getParentModel().getHead().translateAndRotate(pose);
        pose.translate(0.0F, -0.25F, 0.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        pose.scale(SCALE, SCALE, SCALE);
        Portrait.draw(mirror, BuildSites.head(mirror, BuildSites.offsetVector(HatRendering.headwear(entity))), pose, buffers,
                light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
