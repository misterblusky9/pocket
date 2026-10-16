package com.misterblusky9.pocket.client;

import static java.lang.Math.max;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.item.CreativeShrinkRayItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.equipment.zapper.ZapperItemRenderer;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class CreativeShrinkRayRenderer extends ZapperItemRenderer {
    private static final PartialModel CORE = PartialModel.of(id("item/creative_shrink_ray/core"));
    private static final PartialModel CORE_GLOW = PartialModel.of(id("item/creative_shrink_ray/core_glow"));
    private static final PartialModel ACCELERATOR = PartialModel.of(id("item/creative_shrink_ray/accelerator"));

    private static final float READOUT_X = -0.3F;
    private static final float READOUT_Y = -0.45F;
    private static final float READOUT_LEFT = -0.125F;
    private static final float READOUT_HEIGHT = 0.3F;
    private static final float READOUT_MAX_WIDTH = 0.95F;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, path);
    }

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model,
                          PartialItemModelRenderer renderer, ItemDisplayContext transformType,
                          PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        float pt = AnimationTickHolder.getPartialTicks();
        float worldTime = AnimationTickHolder.getRenderTime() / 20.0F;
        renderer.renderSolid(model.getOriginalModel(), light);
        if (transformType == ItemDisplayContext.GUI) renderScaleReadout(stack, ms, buffer);

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean leftHanded = player.getMainArm() == HumanoidArm.LEFT;
        boolean mainHand = player.getMainHandItem() == stack;
        boolean offHand = player.getOffhandItem() == stack;
        float animation = getAnimationProgress(pt, leftHanded, mainHand);

        float multiplier = (mainHand || offHand) ? animation : Mth.sin(worldTime * 5.0F);
        int intensity = (int) (15 * Mth.clamp(multiplier, 0, 1));
        int glowLight = LightTexture.pack(intensity, max(intensity, 4));

        renderer.renderSolidGlowing(CORE.get(), LightTexture.FULL_BRIGHT);
        renderer.renderGlowing(CORE_GLOW.get(), glowLight);

        float angle = worldTime * -25.0F;
        if (mainHand || offHand) angle += 360.0F * animation;
        angle %= 360.0F;
        float offset = -.155F;
        ms.translate(0, offset, 0);
        ms.mulPose(Axis.ZP.rotationDegrees(angle));
        ms.translate(0, -offset, 0);
        renderer.render(ACCELERATOR.get(), light);
    }

    private static void renderScaleReadout(ItemStack stack, PoseStack ms, MultiBufferSource buffer) {
        Font font = Minecraft.getInstance().font;
        String text = HudScaleText.compact(CreativeShrinkRayItem.selectedScale(stack));

        Matrix4f pose = ms.last().pose();
        Vector3f anchor = pose.transformPosition(new Vector3f(READOUT_X, READOUT_Y, 0.0F));
        float unit = pose.getColumn(0, new Vector3f()).length();

        ms.pushPose();
        ms.last().pose().identity().translate(anchor.x, anchor.y, anchor.z + unit).scale(unit);
        ms.last().normal().identity();
        float textScale = Math.min(READOUT_HEIGHT / font.lineHeight, READOUT_MAX_WIDTH / font.width(text));
        ms.translate(READOUT_LEFT, font.lineHeight * textScale * 0.5F, 0.0F);
        ms.scale(textScale, -textScale, textScale);
        font.drawInBatch(text, 0.0F, 0.0F, 0xFFFFFF, true, ms.last().pose(), buffer,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        ms.popPose();
    }
}
