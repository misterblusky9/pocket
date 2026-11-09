package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.HatStandBlockEntity;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.client.RenderFrame;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.joml.Vector3d;

public final class HatFirstPersonRenderer {
    private static long renderedFrame = -1L;

    public static void render(RenderHandEvent event) {
        long frame = RenderFrame.frame();
        if (renderedFrame == frame) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null
                || !minecraft.options.getCameraType().isFirstPerson()
                || minecraft.gameRenderer.getMainCamera().getEntity() != player
                || !HatStandBlockEntity.firstPersonRendered(HatRendering.headwear(player))) return;

        ClientSubLevel mirror = HatRendering.worn(player);
        if (mirror == null) return;

        double playerScale = Pym.entities().scaleOf(player);
        if (!Double.isFinite(playerScale) || playerScale <= 0.0D) return;

        renderedFrame = frame;
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();

        pose.pushPose();
        try {
            Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
            pose.mulPose(view.invert(new Matrix4f()));
            pose.translate(0.0D, 0.0D * playerScale, 0.0D);
            ItemStack headwear = HatRendering.headwear(player);
            float scale = (float) (BuildSites.scale(headwear) * playerScale);
            HatStandBlockEntity.NameEgg egg = HatStandBlockEntity.NameEgg.of(headwear);
            if (egg == HatStandBlockEntity.NameEgg.UPSIDE_DOWN) pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
            pose.scale(scale, scale, scale);
            Vector3d head = BuildSites.head(mirror, BuildSites.offsetVector(headwear), headwear);
            HatRendering.drawHat(BuildHostBlockEntity.token(headwear), mirror, head, egg, headwear.hasFoil(), pose, buffers,
                    event.getPackedLight(), OverlayTexture.NO_OVERLAY, event.getPartialTick());
        } finally {
            pose.popPose();
        }
        if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch();
    }

    private HatFirstPersonRenderer() {}
}
