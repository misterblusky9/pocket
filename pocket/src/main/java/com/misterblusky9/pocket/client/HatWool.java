package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.HatStandBlockEntity.NameEgg;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Vector3dc;

import java.util.function.Consumer;

public final class HatWool {
    private static final int TICKS = 25;
    private static final double MILLIS_PER_TICK = 50.0;
    private static final float SHELL = 1.002F;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final float[][] PORTAL_FACES = {
            {0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1},
            {0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0, 0},
            {1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1},
            {0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0},
            {0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1},
            {0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0}
    };

    private HatWool() {}

    public static boolean changes(NameEgg egg) {
        return egg == NameEgg.JEB || egg == NameEgg.VOID;
    }

    public static int textColour() {
        return colour(Util.getMillis() / MILLIS_PER_TICK) & 0xFFFFFF;
    }

    public static void draw(NameEgg egg, ClientSubLevel mirror, Vector3dc origin, PoseStack pose,
                            MultiBufferSource buffers, int light, int overlay, float partialTick) {
        if (egg == NameEgg.JEB) tint(mirror, origin, pose, buffers, light, overlay, partialTick);
        else if (egg == NameEgg.VOID) portal(mirror, origin, pose, buffers);
    }

    private static void tint(ClientSubLevel mirror, Vector3dc origin, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay, float partialTick) {
        Level level = Minecraft.getInstance().level;
        int colour = colour((level == null ? 0.0 : level.getGameTime()) + (double) partialTick);
        float red = FastColor.ARGB32.red(colour) / 255.0F;
        float green = FastColor.ARGB32.green(colour) / 255.0F;
        float blue = FastColor.ARGB32.blue(colour) / 255.0F;
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(Blocks.WHITE_WOOL.defaultBlockState());
        VertexConsumer consumer = buffers.getBuffer(Sheets.solidBlockSheet());
        eachWool(mirror, origin, pose, last -> {
            for (Direction side : Direction.values())
                for (BakedQuad quad : model.getQuads(null, side, RANDOM, ModelData.EMPTY, null))
                    consumer.putBulkData(last, quad, red, green, blue, 1.0F, light, overlay);
        });
    }

    private static void portal(ClientSubLevel mirror, Vector3dc origin, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.endPortal());
        eachWool(mirror, origin, pose, last -> {
            Matrix4f matrix = last.pose();
            for (float[] face : PORTAL_FACES)
                for (int corner = 0; corner < 4; corner++)
                    consumer.addVertex(matrix, face[corner * 3], face[corner * 3 + 1], face[corner * 3 + 2]);
        });
    }

    private static void eachWool(ClientSubLevel mirror, Vector3dc origin, PoseStack pose, Consumer<PoseStack.Pose> block) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || mirror.isRemoved()) return;
        BoundingBox3ic bounds = mirror.getPlot().getBoundingBox();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++)
            for (int y = bounds.minY(); y <= bounds.maxY(); y++)
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    if (!level.getBlockState(pos.set(x, y, z)).is(BlockTags.WOOL)) continue;
                    pose.pushPose();
                    pose.translate(x - origin.x() + 0.5, y - origin.y() + 0.5, z - origin.z() + 0.5);
                    pose.scale(SHELL, SHELL, SHELL);
                    pose.translate(-0.5F, -0.5F, -0.5F);
                    block.accept(pose.last());
                    pose.popPose();
                }
    }

    private static int colour(double ticks) {
        int dyes = DyeColor.values().length;
        int cycle = Math.floorMod((int) (ticks / TICKS), dyes);
        float progress = (float) ((ticks % TICKS) / TICKS);
        return FastColor.ARGB32.lerp(progress, Sheep.getColor(DyeColor.byId(cycle)),
                Sheep.getColor(DyeColor.byId((cycle + 1) % dyes)));
    }
}
