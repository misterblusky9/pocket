package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import com.simibubi.create.foundation.render.RenderTypes;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class HotGlueGunRenderer extends CustomRenderedItemModelRenderer {
    private static final PartialModel COG = PartialModel.of(id("item/glue_gun/cog"));
    private static final PartialModel GLUE = PartialModel.of(id("item/glue_gun/glue"));
    private static final PartialModel GLUE_CORE = PartialModel.of(id("item/glue_gun/glue_core"));
    private static final ClipMesh GLUE_MESH = new ClipMesh(Direction.Axis.Z);
    private static final ClipMesh GLUE_CORE_MESH = new ClipMesh(Direction.Axis.Z);

    private static final float[] GLUE_END = {17.25F, 17.75F, 18.25F, 18.75F, 23.0F, 23.5F, 24.0F, 24.5F, 25.0F};
    private static final int GLUE_STEPS = GLUE_END.length - 1;
    private static final float GLUE_SHELL_OVERHANG = 0.05355F;

    private static final float COG_PIVOT_X = (8.0125F - 8.0F) / 16.0F;
    private static final float COG_PIVOT_Y = (5.375F - 8.0F) / 16.0F;
    private static final float COG_SPIN_SPEED = -25.0F;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, path);
    }

    private static int glueLevel(ItemStack stack) {
        if (!stack.isDamageableItem()) return GLUE_STEPS;
        int left = stack.getMaxDamage() - stack.getDamageValue();
        return Mth.positiveCeilDiv(left * GLUE_STEPS, stack.getMaxDamage());
    }

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model,
                          PartialItemModelRenderer renderer, ItemDisplayContext transformType,
                          PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        float worldTime = AnimationTickHolder.getRenderTime() / 20.0F;
        renderer.renderSolid(model.getOriginalModel(), light);

        float end = GLUE_END[glueLevel(stack)] / 16.0F;
        int intensity = (int) (15 * pulse(stack));
        boolean glowing = intensity > 0;
        int glueLight = glowing
                ? LightTexture.pack(
                        Math.max(LightTexture.block(light), intensity),
                        Math.max(LightTexture.sky(light), intensity))
                : light;
        GLUE_CORE_MESH.render(GLUE_CORE.get(), end, ms,
                glueBuffer(buffer, stack, glowing ? RenderTypes.itemGlowingSolid() : Sheets.solidBlockSheet()), glueLight);
        GLUE_MESH.render(GLUE.get(), end + GLUE_SHELL_OVERHANG / 16.0F, ms,
                glueBuffer(buffer, stack, glowing ? RenderTypes.itemGlowingTranslucent() : Sheets.translucentCullBlockSheet()), glueLight);

        float angle = (worldTime * COG_SPIN_SPEED) % 360.0F;
        ms.pushPose();
        ms.translate(COG_PIVOT_X, COG_PIVOT_Y, 0);
        ms.mulPose(Axis.ZP.rotationDegrees(angle));
        ms.translate(-COG_PIVOT_X, -COG_PIVOT_Y, 0);
        renderer.render(COG.get(), light);
        ms.popPose();
    }

    private static VertexConsumer glueBuffer(MultiBufferSource buffer, ItemStack stack, RenderType type) {
        return ItemRenderer.getFoilBufferDirect(buffer, type, true, stack.hasFoil());
    }

    private static float pulse(ItemStack stack) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0.0F;
        boolean mainHand = player.getMainHandItem() == stack;
        if (!mainHand && player.getOffhandItem() != stack) return 0.0F;
        boolean rightHand = mainHand ^ player.getMainArm() == HumanoidArm.LEFT;
        float animation = HotGlueGunRenderHandler.INSTANCE.getAnimation(
                rightHand, AnimationTickHolder.getPartialTicks());
        return Mth.clamp(animation * 5.0F, 0.0F, 1.0F);
    }
}
