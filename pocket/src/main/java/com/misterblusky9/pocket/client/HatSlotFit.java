package com.misterblusky9.pocket.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class HatSlotFit {
    private static final float HEADWEAR_HALF = 0.3F;

    public static void apply(PoseStack pose, ItemDisplayContext context, boolean leftHand, ClientSubLevel mirror,
                             Vector3d head, Vector3d frame, boolean upsideDown, float headwearScale) {
        if (context != ItemDisplayContext.GUI) return;
        BoundingBox3ic bounds = mirror.getPlot().getBoundingBox();
        if (bounds == null) return;
        BakedModel model = Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(Items.PLAYER_HEAD);
        PoseStack probe = new PoseStack();
        ClientHooks.handleCameraTransforms(probe, model, context, leftHand);
        probe.translate(0.0F, -0.25F, 0.0F);
        probe.mulPose(Axis.YP.rotationDegrees(180.0F));
        probe.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
        if (upsideDown) {
            probe.translate(head.x - frame.x, head.y - frame.y, head.z - frame.z);
            probe.mulPose(Axis.ZP.rotationDegrees(180.0F));
            probe.translate(frame.x - head.x, frame.y - head.y, frame.z - head.z);
        }
        Matrix4f matrix = probe.last().pose();
        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int corner = 0; corner < 8; corner++) {
            include(matrix, box,
                    ((corner & 1) == 0 ? bounds.minX() : bounds.maxX() + 1) - frame.x,
                    ((corner & 2) == 0 ? bounds.minY() : bounds.maxY() + 1) - frame.y,
                    ((corner & 4) == 0 ? bounds.minZ() : bounds.maxZ() + 1) - frame.z);
        }
        if (headwearScale > 0.0F) {
            float half = HEADWEAR_HALF * headwearScale;
            for (int corner = 0; corner < 8; corner++) {
                include(matrix, box,
                        head.x - frame.x + ((corner & 1) == 0 ? -half : half),
                        head.y - frame.y + ((corner & 2) == 0 ? -half : half),
                        head.z - frame.z + ((corner & 4) == 0 ? -half : half));
            }
        }
        float size = Math.max(box[2] - box[0], box[3] - box[1]);
        if (size <= 1.0F) return;
        float fit = 1.0F / size;
        pose.scale(fit, fit, fit);
        pose.translate(-(box[0] + box[2]) / 2.0F, -(box[1] + box[3]) / 2.0F, 0.0F);
    }

    private static void include(Matrix4f matrix, float[] box, double x, double y, double z) {
        Vector3f point = matrix.transformPosition((float) x, (float) y, (float) z, new Vector3f());
        box[0] = Math.min(box[0], point.x);
        box[1] = Math.min(box[1], point.y);
        box[2] = Math.max(box[2], point.x);
        box[3] = Math.max(box[3], point.y);
    }

    private HatSlotFit() {}
}
