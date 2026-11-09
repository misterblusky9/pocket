package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.HatStandBlock;
import com.misterblusky9.pocket.block.HatStandBlockEntity;
import com.misterblusky9.pocket.build.BuildHostBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class HatStandOutline {
    private static final float ALPHA = 0.4F;
    private static final int[][] EDGES = {
            {0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3},
            {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}
    };

    private HatStandOutline() {}

    public static void render(RenderHighlightEvent.Block event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return;
        BlockPos pos = event.getTarget().getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof HatStandBlock block) || state.getValue(BuildHostBlock.ACTIVE)
                || !block.holdsHead(state)) return;
        BlockPos standPos = block.standPos(state, pos);
        if (!(level.getBlockEntity(standPos) instanceof HatStandBlockEntity stand)) return;
        event.setCanceled(true);

        float pt = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack.Pose pose = event.getPoseStack().last();
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        ClientSubLevel host = Sable.HELPER.getContaining(level, pos) instanceof ClientSubLevel sub && !sub.isRemoved()
                ? sub : null;
        Vec3 origin = Vec3.atLowerCornerOf(pos);

        state.setValue(BuildHostBlock.ACTIVE, true).getShape(level, pos).forAllEdges((x1, y1, z1, x2, y2, z2) -> line(pose, lines,
                world(host, origin.add(x1, y1, z1), pt, camera), world(host, origin.add(x2, y2, z2), pt, camera)));

        AABB head = stand.headBox().move(standPos);
        Quaterniond turn = stand.headTurn(pt);
        Vec3[] corners = new Vec3[8];
        for (int i = 0; i < corners.length; i++) {
            Vec3 rest = new Vec3(head.getCenter().x + ((i & 1) == 0 ? -1 : 1) * head.getXsize() * HatStandRenderer.HEAD_SCALE / 2.0,
                    head.minY + ((i & 2) == 0 ? 0.0 : head.getYsize() * HatStandRenderer.HEAD_SCALE),
                    head.getCenter().z + ((i & 4) == 0 ? -1 : 1) * head.getZsize() * HatStandRenderer.HEAD_SCALE / 2.0);
            corners[i] = world(host, stand.turnAboutPivot(turn, rest), pt, camera);
        }
        for (int[] edge : EDGES) line(pose, lines, corners[edge[0]], corners[edge[1]]);
    }

    private static Vec3 world(ClientSubLevel host, Vec3 plot, float pt, Vec3 camera) {
        if (host == null) return plot.subtract(camera);
        Vector3d world = host.renderPose(pt).transformPosition(new Vector3d(plot.x, plot.y, plot.z));
        return new Vec3(world.x - camera.x, world.y - camera.y, world.z - camera.z);
    }

    private static void line(PoseStack.Pose pose, VertexConsumer lines, Vec3 from, Vec3 to) {
        Vector3f normal = new Vector3f((float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z));
        if (normal.lengthSquared() <= 1.0E-12F) return;
        normal.normalize();
        lines.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(0.0F, 0.0F, 0.0F, ALPHA)
                .setNormal(pose, normal.x, normal.y, normal.z);
        lines.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                .setColor(0.0F, 0.0F, 0.0F, ALPHA)
                .setNormal(pose, normal.x, normal.y, normal.z);
    }
}
