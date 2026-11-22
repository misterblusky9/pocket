package com.misterblusky9.pocket.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.misterblusky9.pocket.item.PocketContainer;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The Creative Case uses exactly one model JSON. Its #15 quads are passed to
 * RenderType.endPortal() and omitted from the ordinary textured-quad pass.
 * Their geometry and UVs come from that same baked model, not a second shell
 * JSON or hardcoded positions. The portal is drawn on the inward-facing side.
 */
public final class CreativeCasePortal {
    // The #15 atlas entry in creative_pocket_case.json is a dedicated marker.
    private static final ResourceLocation PORTAL_MARKER =
            ResourceLocation.withDefaultNamespace("block/obsidian");
    public static final double GROUND_DROP = 2.0D / 16.0D;
    private static BakedModel cachedModel;
    private static List<BakedQuad> shellQuads = List.of();
    private static List<BakedQuad> portalQuads = List.of();

    public static void invalidate() {
        cachedModel = null;
        shellQuads = List.of();
        portalQuads = List.of();
    }

    public static void renderShell(ItemStack stack, PoseStack poses, MultiBufferSource buffers,
                                   int light, int overlay) {
        renderShell(stack, poses, buffers, light, overlay, itemModel());
    }

    public static void renderShell(ItemStack stack, PoseStack poses, MultiBufferSource buffers,
                                   int light, int overlay, BakedModel model) {
        resolve(model);
        if (shellQuads.isEmpty()) return;

        poses.pushPose();
        // The Create custom item renderer supplies model-centred coordinates.
        poses.translate(-0.5D, -0.5D, -0.5D);
        VertexConsumer consumer = ItemRenderer.getFoilBufferDirect(
                buffers, Sheets.solidBlockSheet(), true, stack.hasFoil());
        Minecraft.getInstance().getItemRenderer().renderQuadList(
                poses, consumer, shellQuads, stack, light, overlay);
        poses.popPose();
    }

    public static void renderPortal(PoseStack poses, MultiBufferSource buffers) {
        renderPortal(poses, buffers, itemModel());
    }

    public static void renderPortal(PoseStack poses, MultiBufferSource buffers, BakedModel model) {
        resolve(model);
        if (portalQuads.isEmpty()) return;

        poses.pushPose();
        poses.translate(-0.5D, -0.5D, -0.5D);
        Matrix4f matrix = poses.last().pose();
        VertexConsumer portal = buffers.getBuffer(RenderType.endPortal());
        for (BakedQuad quad : portalQuads) {
            int[] data = quad.getVertices();
            if (data.length % 4 != 0) continue;
            int stride = data.length / 4;
            if (stride < 3) continue;
            // JSON cuboid faces are outward-wound; reveal their inside surface.
            for (int v = 3; v >= 0; v--) {
                int offset = v * stride;
                portal.addVertex(matrix, Float.intBitsToFloat(data[offset]),
                        Float.intBitsToFloat(data[offset + 1]),
                        Float.intBitsToFloat(data[offset + 2]));
            }
        }
        poses.popPose();
    }

    private static BakedModel itemModel() {
        return Minecraft.getInstance().getItemRenderer().getItemModelShaper()
                .getItemModel(PocketContainer.CREATIVE_POCKET_CASE.item());
    }

    private static void resolve(BakedModel baked) {
        if (baked instanceof CustomRenderedItemModel wrapped) baked = wrapped.getOriginalModel();
        if (baked == cachedModel) return;
        cachedModel = baked;
        if (baked == null) {
            shellQuads = portalQuads = List.of();
            return;
        }
        List<BakedQuad> shell = new ArrayList<>();
        List<BakedQuad> portal = new ArrayList<>();
        RandomSource random = RandomSource.create(42L);
        for (Direction face : Direction.values()) {
            random.setSeed(42L);
            classify(baked.getQuads(null, face, random, ModelData.EMPTY, null), shell, portal);
        }
        random.setSeed(42L);
        classify(baked.getQuads(null, null, random, ModelData.EMPTY, null), shell, portal);
        shellQuads = List.copyOf(shell);
        portalQuads = List.copyOf(portal);
    }

    private static void classify(List<BakedQuad> quads, List<BakedQuad> shell, List<BakedQuad> portal) {
        for (BakedQuad quad : quads) {
            (PORTAL_MARKER.equals(quad.getSprite().contents().name()) ? portal : shell).add(quad);
        }
    }

    private CreativeCasePortal() {}
}
