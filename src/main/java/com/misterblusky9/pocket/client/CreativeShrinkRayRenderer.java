package com.misterblusky9.pocket.client;

import static java.lang.Math.max;

import com.misterblusky9.pocket.PocketSized;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.equipment.zapper.ZapperItemRenderer;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class CreativeShrinkRayRenderer extends ZapperItemRenderer {
    private static final PartialModel CORE = PartialModel.of(id("item/creative_shrink_ray/core"));
    private static final PartialModel CORE_GLOW = PartialModel.of(id("item/creative_shrink_ray/core_glow"));
    private static final PartialModel ACCELERATOR = PartialModel.of(id("item/creative_shrink_ray/accelerator"));

    private static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, path);
    }

    @Override
    protected void render(final ItemStack stack, final CustomRenderedItemModel model,
                          final PartialItemModelRenderer renderer, final ItemDisplayContext transformType,
                          final PoseStack ms, final MultiBufferSource buffer, final int light, final int overlay) {
        final float pt = AnimationTickHolder.getPartialTicks();
        final float worldTime = AnimationTickHolder.getRenderTime() / 20.0F;
        renderer.renderSolid(model.getOriginalModel(), light);

        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        final boolean leftHanded = player.getMainArm() == HumanoidArm.LEFT;
        final boolean mainHand = player.getMainHandItem() == stack;
        final boolean offHand = player.getOffhandItem() == stack;
        final float animation = getAnimationProgress(pt, leftHanded, mainHand);
        final float multiplier = (mainHand || offHand) ? animation : Mth.sin(worldTime * 5.0F);
        final int intensity = (int) (15 * Mth.clamp(multiplier, 0, 1));
        final int glowLight = LightTexture.pack(intensity, max(intensity, 4));
        renderer.renderSolidGlowing(CORE.get(), glowLight);
        renderer.renderGlowing(CORE_GLOW.get(), glowLight);

        float angle = worldTime * -25.0F;
        if (mainHand || offHand) angle += 360.0F * animation;
        angle %= 360.0F;
        final float offset = -.155F;
        ms.translate(0, offset, 0);
        ms.mulPose(Axis.ZP.rotationDegrees(angle));
        ms.translate(0, -offset, 0);
        renderer.render(ACCELERATOR.get(), light);
    }
}
