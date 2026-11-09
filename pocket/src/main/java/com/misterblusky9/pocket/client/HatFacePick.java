package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.build.BuildHostBlock;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.build.BuildSites;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Optional;

public final class HatFacePick {
    private static final float ALPHA = 0.4F;
    private static final double EDGE_EPSILON = 1.0E-5;
    private static final int[][] CUBE_EDGES = {
            {0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3},
            {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}
    };

    private static @Nullable BlockHitResult target;
    private static @Nullable HitResult swapped;

    private record Graze(BlockHitResult hit, double distanceSqr) {}

    public static void beginUse() {
        Minecraft minecraft = Minecraft.getInstance();
        if (target == null) return;
        swapped = minecraft.hitResult;
        minecraft.hitResult = target;
    }

    public static void endUse() {
        if (swapped == null) return;
        Minecraft.getInstance().hitResult = swapped;
        swapped = null;
    }

    public static void refine(float partialTick) {
        target = null;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !(minecraft.getCameraEntity() instanceof LocalPlayer player) || player.isSpectator()
                || !(player.getMainHandItem().getItem() instanceof BlockItem)
                && !(player.getOffhandItem().getItem() instanceof BlockItem)) return;
        HitResult current = minecraft.hitResult;
        if (current instanceof BlockHitResult block && current.getType() == HitResult.Type.BLOCK
                && !level.getBlockState(block.getBlockPos()).is(ModBlocks.POCKET_DUMMY.get())
                && Sable.HELPER.getContaining(level, block.getBlockPos()) instanceof ClientSubLevel aimed
                && BuildMirrors.host(aimed) != null) return;
        Vec3 eye = Sable.HELPER.getEyePositionInterpolated(player, partialTick);
        if (eye == null) return;
        double reach = player.blockInteractionRange();
        Vec3 end = eye.add(player.getViewVector(partialTick).scale(reach));
        double limit = current == null || current.getType() == HitResult.Type.MISS ? reach * reach
                : Sable.HELPER.distanceSquaredWithSubLevels(level, eye, current.getLocation());
        BlockHitResult best = null;
        for (ClientSubLevel mirror : BuildMirrors.standingMirrors()) {
            Graze graze = graze(level, mirror, partialTick, eye, end);
            if (graze == null || graze.distanceSqr() >= limit) continue;
            limit = graze.distanceSqr();
            best = graze.hit();
        }
        target = best;
    }

    private static @Nullable Graze graze(ClientLevel level, ClientSubLevel mirror, float partialTick, Vec3 eye, Vec3 end) {
        BlockPos hostPos = BuildMirrors.host(mirror);
        if (hostPos == null || !(level.getBlockEntity(hostPos) instanceof BuildHostBlockEntity host)
                || !(host.getBlockState().getBlock() instanceof BuildHostBlock block)) return null;
        int size = block.scaffoldSize();
        BlockPos origin = BuildSites.scaffoldOrigin(mirror, size, host.getOffset());
        AABB box = new AABB(Vec3.atLowerCornerOf(origin), Vec3.atLowerCornerOf(origin.offset(size, size, size)));
        Pose3dc pose = mirror.renderPose(partialTick);
        Vec3 from = local(pose, eye);
        Vec3 to = local(pose, end);
        Vec3 entry = box.contains(from) ? from : box.clip(from, to).orElse(null);
        if (entry == null) return null;
        Vec3 exit = box.contains(to) ? to : box.clip(to, from).orElse(to);
        return BlockGetter.traverseBlocks(entry, exit, level,
                (getter, cell) -> visit(getter, cell, from, to, pose, eye),
                ignored -> Optional.<Graze>empty()).orElse(null);
    }

    private static @Nullable Optional<Graze> visit(ClientLevel level, BlockPos cell, Vec3 from, Vec3 to,
                                                   Pose3dc pose, Vec3 eye) {
        if (!level.getBlockState(cell).isAir()) return Optional.empty();
        AABB unit = new AABB(cell);
        Vec3 in = unit.contains(from) ? from : unit.clip(from, to).orElse(null);
        Vec3 out = unit.contains(to) ? to : unit.clip(to, from).orElse(in);
        if (in == null) in = out;
        if (in == null) return null;
        double distanceSqr = world(pose, in).distanceToSqr(eye);
        for (Direction toward : Direction.values()) {
            BlockPos dummy = cell.relative(toward);
            if (!level.getBlockState(dummy).is(ModBlocks.POCKET_DUMMY.get())) continue;
            Direction face = toward.getOpposite();
            Vec3 location = onFace(in.add(out).scale(0.5), dummy, face);
            return Optional.of(new Graze(new BlockHitResult(location, face, dummy.immutable(), false), distanceSqr));
        }
        if (!touchesDummy(level, cell)) return null;
        return Optional.of(new Graze(new BlockHitResult(in, entered(cell, in, to.subtract(from)), cell.immutable(), false),
                distanceSqr));
    }

    private static boolean touchesDummy(ClientLevel level, BlockPos cell) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (distance == 0 || distance == 3) continue;
                    if (level.getBlockState(cell.offset(dx, dy, dz)).is(ModBlocks.POCKET_DUMMY.get())) return true;
                }
            }
        }
        return false;
    }

    private static Direction entered(BlockPos cell, Vec3 in, Vec3 ray) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            double offset = in.get(axis) - cell.get(axis);
            if (offset < EDGE_EPSILON) return Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
            if (offset > 1.0 - EDGE_EPSILON) return Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        }
        return Direction.getNearest(-ray.x, -ray.y, -ray.z);
    }

    private static Vec3 onFace(Vec3 point, BlockPos pos, Direction face) {
        double x = Math.clamp(point.x, pos.getX(), pos.getX() + 1.0);
        double y = Math.clamp(point.y, pos.getY(), pos.getY() + 1.0);
        double z = Math.clamp(point.z, pos.getZ(), pos.getZ() + 1.0);
        double plane = pos.get(face.getAxis())
                + (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 : 0.0);
        return switch (face.getAxis()) {
            case X -> new Vec3(plane, y, z);
            case Y -> new Vec3(x, plane, z);
            case Z -> new Vec3(x, y, plane);
        };
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        HitResult aimed = target != null ? target : minecraft.hitResult;
        if (level == null || minecraft.options.hideGui || !(aimed instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        boolean dummy = level.getBlockState(pos).is(ModBlocks.POCKET_DUMMY.get());
        if (!dummy && !level.getBlockState(pos).isAir()
                || !(Sable.HELPER.getContaining(level, pos) instanceof ClientSubLevel mirror)
                || BuildMirrors.host(mirror) == null) return;
        BlockPos cell = dummy ? pos.relative(hit.getDirection()) : pos;
        if (!level.getBlockState(cell).isAir()) return;
        Pose3dc pose = mirror.renderPose(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        Vec3 camera = event.getCamera().getPosition();
        Vec3[] corners = new Vec3[8];
        for (int i = 0; i < corners.length; i++)
            corners[i] = world(pose, new Vec3(cell.getX() + (i & 1), cell.getY() + (i >> 1 & 1),
                    cell.getZ() + (i >> 2 & 1))).subtract(camera);
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(PocketRenderTypes.FACE_OUTLINE);
        for (int[] edge : CUBE_EDGES) line(poseStack, lines, corners[edge[0]], corners[edge[1]]);
        buffers.endBatch(PocketRenderTypes.FACE_OUTLINE);
    }

    private static void line(PoseStack poseStack, VertexConsumer lines, Vec3 from, Vec3 to) {
        PoseStack.Pose last = poseStack.last();
        Vector3f normal = new Vector3f((float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z));
        if (normal.lengthSquared() <= 1.0E-12F) return;
        normal.normalize();
        lines.addVertex(last, (float) from.x, (float) from.y, (float) from.z)
                .setColor(0.0F, 0.0F, 0.0F, ALPHA).setNormal(last, normal.x, normal.y, normal.z);
        lines.addVertex(last, (float) to.x, (float) to.y, (float) to.z)
                .setColor(0.0F, 0.0F, 0.0F, ALPHA).setNormal(last, normal.x, normal.y, normal.z);
    }

    private static Vec3 local(Pose3dc pose, Vec3 point) {
        Vector3d local = pose.transformPositionInverse(new Vector3d(point.x, point.y, point.z));
        return new Vec3(local.x, local.y, local.z);
    }

    private static Vec3 world(Pose3dc pose, Vec3 point) {
        Vector3d world = pose.transformPosition(new Vector3d(point.x, point.y, point.z));
        return new Vec3(world.x, world.y, world.z);
    }

    private HatFacePick() {}
}
