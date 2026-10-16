package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.compat.simulated.WeldContact;
import com.misterblusky9.pocket.compat.simulated.WeldGeometry;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.util.SimColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

public final class HotGlueFacePicker {
    private static final double EDGE_LIMIT = 0.15D;
    private static final double CENTRE_MIN = 0.33D;
    private static final double CENTRE_MAX = 0.67D;
    private static final double FACE_VISIBILITY_EPSILON = 0.015D;
    private static final int INACTIVE_COLOR = 0x8C979F;

    private static final WeldContactPatch.Edge U_MIN =
            new WeldContactPatch.Edge(-0.495D, -0.14D, -0.435D, 0.14D);
    private static final WeldContactPatch.Edge U_MAX =
            new WeldContactPatch.Edge(0.435D, -0.14D, 0.495D, 0.14D);
    private static final WeldContactPatch.Edge V_MIN =
            new WeldContactPatch.Edge(-0.14D, -0.495D, 0.14D, -0.435D);
    private static final WeldContactPatch.Edge V_MAX =
            new WeldContactPatch.Edge(-0.14D, 0.435D, 0.14D, 0.495D);

    static Direction edgeFacing(Direction hitFace, double u, double v) {
        if (hitFace == null || !Double.isFinite(u) || !Double.isFinite(v)) return hitFace;
        Direction.Axis uAxis = WeldGeometry.uAxis(hitFace);
        Direction.Axis vAxis = WeldGeometry.vAxis(hitFace);
        if (v >= CENTRE_MIN && v <= CENTRE_MAX) {
            if (u >= 0.0D && u <= EDGE_LIMIT) return direction(uAxis, false);
            if (u >= 1.0D - EDGE_LIMIT && u <= 1.0D) return direction(uAxis, true);
        }
        if (u >= CENTRE_MIN && u <= CENTRE_MAX) {
            if (v >= 0.0D && v <= EDGE_LIMIT) return direction(vAxis, false);
            if (v >= 1.0D - EDGE_LIMIT && v <= 1.0D) return direction(vAxis, true);
        }
        return hitFace;
    }

    private static Direction direction(Direction.Axis axis, boolean positive) {
        return Direction.fromAxisAndDirection(axis,
                positive ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
    }

    static boolean hiddenFromEye(BlockPos pos, Direction face, Vec3 localEye) {
        if (pos == null || face == null || localEye == null) return false;
        double plane = pos.get(face.getAxis())
                + (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D : 0.0D);
        double signedDistance = (coordinate(localEye, face.getAxis()) - plane)
                * face.getAxisDirection().getStep();
        return Double.isFinite(signedDistance) && signedDistance <= FACE_VISIBILITY_EPSILON;
    }

    public static BlockHitResult select(Level level, LocalPlayer player, BlockHitResult hit) {
        if (level == null || player == null || hit == null || hit.getType() != HitResult.Type.BLOCK) return hit;
        Direction desired = requestedFace(hit);
        if (desired == hit.getDirection() || !open(level, hit.getBlockPos(), desired)) return hit;
        Vec3 eye = eyeInPlot(level, player, hit.getBlockPos());
        if (!hiddenFromEye(hit.getBlockPos(), desired, eye)) return hit;
        return remap(hit, desired);
    }

    private static Direction requestedFace(BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Vec3 fraction = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
        Direction.Axis u = WeldGeometry.uAxis(hit.getDirection());
        Direction.Axis v = WeldGeometry.vAxis(hit.getDirection());
        return edgeFacing(hit.getDirection(), coordinate(fraction, u), coordinate(fraction, v));
    }

    static BlockHitResult remap(BlockHitResult hit, Direction selected) {
        BlockPos pos = hit.getBlockPos();
        Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
        double x = clamp(local.x);
        double y = clamp(local.y);
        double z = clamp(local.z);

        switch (hit.getDirection().getAxis()) {
            case X -> x = 0.5D;
            case Y -> y = 0.5D;
            case Z -> z = 0.5D;
        }
        double plane = selected.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D : 0.0D;
        switch (selected.getAxis()) {
            case X -> x = plane;
            case Y -> y = plane;
            case Z -> z = plane;
        }
        return new BlockHitResult(new Vec3(pos.getX() + x, pos.getY() + y, pos.getZ() + z),
                selected, pos, hit.isInside());
    }

    public static void render(Level level, LocalPlayer player, BlockHitResult hit) {
        if (level == null || player == null || hit == null || hit.getType() != HitResult.Type.BLOCK
                || level.getBlockState(hit.getBlockPos()).isAir()) return;

        BlockPos pos = hit.getBlockPos();
        Direction facing = hit.getDirection();
        Direction.Axis u = WeldGeometry.uAxis(facing);
        Direction.Axis v = WeldGeometry.vAxis(facing);
        Vec3 eye = eyeInPlot(level, player, pos);
        if (eye == null) return;

        List<WeldContactPatch.Edge> ticks = new ArrayList<>(4);
        addIfNeeded(level, pos, direction(u, false), eye, U_MIN, ticks);
        addIfNeeded(level, pos, direction(u, true), eye, U_MAX, ticks);
        addIfNeeded(level, pos, direction(v, false), eye, V_MIN, ticks);
        addIfNeeded(level, pos, direction(v, true), eye, V_MAX, ticks);
        if (ticks.isEmpty()) return;

        Vector3d centre = WeldGeometry.faceCentre(pos, facing);
        WeldContactPatch.showFaces("pocket_weld_face_tabs", pos, facing, centre,
                WeldContact.identity(facing), ticks, INACTIVE_COLOR);

        Direction hovered = requestedFace(hit);
        if (hovered == facing || !open(level, pos, hovered) || !hiddenFromEye(pos, hovered, eye)) return;
        WeldContactPatch.Edge tick = hovered == direction(u, false) ? U_MIN
                : hovered == direction(u, true) ? U_MAX
                : hovered == direction(v, false) ? V_MIN : V_MAX;
        WeldContactPatch.showFaces("pocket_weld_face_tab_active", pos, facing, centre,
                WeldContact.identity(facing), List.of(tick), SimColors.SUCCESS_LIME);
    }

    private static void addIfNeeded(Level level, BlockPos pos, Direction face,
                                    Vec3 eye, WeldContactPatch.Edge tick,
                                    List<WeldContactPatch.Edge> ticks) {
        if (open(level, pos, face) && hiddenFromEye(pos, face, eye)) ticks.add(tick);
    }

    private static boolean open(Level level, BlockPos pos, Direction side) {
        return level.getBlockState(pos.relative(side)).isAir();
    }

    private static Vec3 eyeInPlot(Level level, LocalPlayer player, BlockPos pos) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float partialTick = camera.isInitialized() ? camera.getPartialTickTime() : 1.0F;
        Vec3 worldEye = Sable.HELPER.getEyePositionInterpolated(player, partialTick);
        if (worldEye == null || !Double.isFinite(worldEye.x)
                || !Double.isFinite(worldEye.y) || !Double.isFinite(worldEye.z)) return null;

        Vector3d eye = new Vector3d(worldEye.x, worldEye.y, worldEye.z);
        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        if (subLevel instanceof ClientSubLevel client && !client.isRemoved()) {
            client.renderPose(partialTick).transformPositionInverse(eye);
        } else if (subLevel != null && !subLevel.isRemoved()) {
            subLevel.logicalPose().transformPositionInverse(eye);
        }
        return new Vec3(eye.x, eye.y, eye.z);
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static double coordinate(Vec3 value, Direction.Axis axis) {
        return switch (axis) {
            case X -> value.x;
            case Y -> value.y;
            case Z -> value.z;
        };
    }

    private HotGlueFacePicker() {}
}
