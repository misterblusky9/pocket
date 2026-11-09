package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.HatStandBlockEntity;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.build.BuildSites;
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
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3d;

public final class HatLayer<T extends LivingEntity, M extends EntityModel<T> & HeadedModel> extends RenderLayer<T, M> {
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
        ItemStack headwear = HatRendering.headwear(entity);
        float scale = (float) BuildSites.scale(headwear);
        pose.scale(scale, scale, scale);
        HatRendering.pullToCamera(pose);
        Vector3d head = BuildSites.head(mirror, BuildSites.offsetVector(headwear), headwear);
        HatStandBlockEntity.NameEgg egg = HatStandBlockEntity.NameEgg.of(headwear);
        if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch();
        HatRendering.drawHat(BuildHostBlockEntity.token(headwear), mirror, head, egg, headwear.hasFoil(), pose, buffers, light,
                OverlayTexture.NO_OVERLAY, partialTick);
        pose.popPose();
    }
}
