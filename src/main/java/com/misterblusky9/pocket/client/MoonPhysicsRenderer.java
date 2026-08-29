package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

public final class MoonPhysicsRenderer {

    public static void render(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimensionType().hasSkyLight()) return;

        final float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        final MoonPhysicsClient.RenderState state = MoonPhysicsClient.renderState(partialTick);
        if (state == null || state.halfExtent() <= 0.0D) return;

        final Vec3 camera = event.getCamera().getPosition();
        final double diameter = state.halfExtent() * 2.0D;
        final PoseStack poses = event.getPoseStack();
        final MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        final int light = LevelRenderer.getLightColor(
                minecraft.level,
                BlockPos.containing(state.position())
        );

        poses.pushPose();
        poses.translate(
                state.position().x - camera.x,
                state.position().y - camera.y,
                state.position().z - camera.z
        );
        poses.mulPose(new Quaternionf(
                (float) state.orientation().x(),
                (float) state.orientation().y(),
                (float) state.orientation().z(),
                (float) state.orientation().w()
        ));
        poses.scale((float) diameter, (float) diameter, (float) diameter);

        minecraft.getItemRenderer().renderStatic(
                new ItemStack(ModItems.THE_MOON.get()),
                ItemDisplayContext.NONE,
                light,
                OverlayTexture.NO_OVERLAY,
                poses,
                buffers,
                minecraft.level,
                0
        );
        buffers.endBatch();
        poses.popPose();
    }

    private MoonPhysicsRenderer() {}
}
