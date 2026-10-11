package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
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

public final class HotGlueGunRenderer extends CustomRenderedItemModelRenderer {
    private static final PartialModel COG = PartialModel.of(id("item/glue_gun/cog"));
    private static final int GLUE_STEPS = 8;
    private static final PartialModel[] GLUE = levels("item/glue_gun/glue_");
    private static final PartialModel[] GLUE_CORE = levels("item/glue_gun/glue_core_");

    private static final float COG_PIVOT_X = (8.0125F - 8.0F) / 16.0F;
    private static final float COG_PIVOT_Y = (5.375F - 8.0F) / 16.0F;
    private static final float COG_SPIN_SPEED = -25.0F;

    private static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, path);
    }

    private static PartialModel[] levels(final String prefix) {
        final PartialModel[] levels = new PartialModel[GLUE_STEPS + 1];
        for (int level = 0; level <= GLUE_STEPS; level++) levels[level] = PartialModel.of(id(prefix + level));
        return levels;
    }

    private static int glueLevel(final ItemStack stack) {
        if (!stack.isDamageableItem()) return GLUE_STEPS;
        final int left = stack.getMaxDamage() - stack.getDamageValue();
        return Mth.clamp(Mth.positiveCeilDiv(left * GLUE_STEPS, stack.getMaxDamage()), 0, GLUE_STEPS);
    }

    @Override
    protected void render(final ItemStack stack, final CustomRenderedItemModel model,
                          final PartialItemModelRenderer renderer, final ItemDisplayContext transformType,
                          final PoseStack ms, final MultiBufferSource buffer, final int light, final int overlay) {
        final float worldTime = AnimationTickHolder.getRenderTime() / 20.0F;
        renderer.renderSolid(model.getOriginalModel(), light);

        final int level = glueLevel(stack);
        final int intensity = (int) (15 * pulse(stack));
        if (intensity <= 0) {
            renderer.renderSolid(GLUE_CORE[level].get(), light);
            renderer.render(GLUE[level].get(), light);
        } else {
            final int glow = LightTexture.pack(
                    Math.max(LightTexture.block(light), intensity),
                    Math.max(LightTexture.sky(light), intensity));
            renderer.renderSolidGlowing(GLUE_CORE[level].get(), glow);
            renderer.renderGlowing(GLUE[level].get(), glow);
        }

        final float angle = (worldTime * COG_SPIN_SPEED) % 360.0F;
        ms.pushPose();
        ms.translate(COG_PIVOT_X, COG_PIVOT_Y, 0);
        ms.mulPose(Axis.ZP.rotationDegrees(angle));
        ms.translate(-COG_PIVOT_X, -COG_PIVOT_Y, 0);
        renderer.render(COG.get(), light);
        ms.popPose();
    }

    private static float pulse(final ItemStack stack) {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0.0F;
        final boolean mainHand = player.getMainHandItem() == stack;
        if (!mainHand && player.getOffhandItem() != stack) return 0.0F;
        final boolean rightHand = mainHand ^ player.getMainArm() == HumanoidArm.LEFT;
        final float animation = HotGlueGunRenderHandler.INSTANCE.getAnimation(
                rightHand, AnimationTickHolder.getPartialTicks());
        return Mth.clamp(animation * 5.0F, 0.0F, 1.0F);
    }
}
