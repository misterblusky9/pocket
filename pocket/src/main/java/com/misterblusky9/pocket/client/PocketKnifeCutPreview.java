package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import com.misterblusky9.pocket.compat.simulated.WeldContact;
import com.misterblusky9.pocket.compat.simulated.WeldGeometry;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pym.api.Pym;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.util.SimColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Vector3d;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = PocketSized.MOD_ID, value = Dist.CLIENT)
public final class PocketKnifeCutPreview {
    private static final List<WeldContactPatch.Edge> FULL_FACE = List.of(
            new WeldContactPatch.Edge(-0.495D, -0.495D, 0.495D, 0.495D)
    );

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null || minecraft.isPaused()) return;
        if (!holdingKnife(player)) return;

        if (!(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        if (CrossScaleWelds.weldNear(level, hit.getLocation()) != null) return;

        SubLevel subLevel = Sable.HELPER.getContaining(level, hit.getBlockPos());
        if (subLevel == null || subLevel.isRemoved()) return;
        if (!Pym.scale().isSettled(subLevel)) return;

        List<FacePatch> faces = buildPreview(level, subLevel, hit);
        if (faces == null) return;

        for (FacePatch patch : faces) {
            Vector3d centre = WeldGeometry.faceCentre(patch.pos(), patch.face());
            WeldContactPatch.showFaces(
                    "pocket_knife_cut_preview_fill_" + patch.key(),
                    patch.pos(),
                    patch.face(),
                    centre,
                    WeldContact.identity(patch.face()),
                    FULL_FACE,
                    SimColors.SUCCESS_LIME
            );
        }
    }

    private static List<FacePatch> buildPreview(Level level, SubLevel subLevel, BlockHitResult hit) {
        if (subLevel.getPlot() == null) return null;
        BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        if (bounds == null) return null;

        Direction clickedFace = hit.getDirection();
        Direction.Axis axis = clickedFace.getAxis();
        boolean movePositive = clickedFace.getAxisDirection() == Direction.AxisDirection.POSITIVE;

        int cut = movePositive
                ? coordinate(hit.getBlockPos(), axis) + 1
                : coordinate(hit.getBlockPos(), axis);

        Direction faceTowardOtherHalf = Direction.fromAxisAndDirection(
                axis,
                movePositive ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE
        );

        int solid = 0;
        int moved = 0;
        Set<FacePatch> faces = new LinkedHashSet<>();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;

                    solid++;

                    if (!inMovedHalf(pos, axis, cut, movePositive)) continue;
                    moved++;

                    if (!touchesSeam(pos, axis, cut, movePositive)) continue;

                    neighbor.set(pos).move(faceTowardOtherHalf);
                    BlockState otherState = level.getBlockState(neighbor);
                    if (otherState.isAir() || inMovedHalf(neighbor, axis, cut, movePositive)) continue;

                    faces.add(new FacePatch(pos.immutable(), faceTowardOtherHalf, key(pos, faceTowardOtherHalf)));
                    faces.add(new FacePatch(
                            neighbor.immutable(),
                            faceTowardOtherHalf.getOpposite(),
                            key(neighbor, faceTowardOtherHalf.getOpposite())
                    ));
                }
            }
        }

        if (solid == 0 || moved == 0 || moved == solid || faces.isEmpty()) return null;
        return List.copyOf(faces);
    }

    private static boolean holdingKnife(LocalPlayer player) {
        return isKnife(player.getMainHandItem()) || isKnife(player.getOffhandItem());
    }

    private static boolean isKnife(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModItems.POCKET_KNIFE.get());
    }

    private static boolean inMovedHalf(BlockPos pos, Direction.Axis axis, int cut, boolean movePositive) {
        int value = coordinate(pos, axis);
        return movePositive ? value >= cut : value < cut;
    }

    private static boolean touchesSeam(BlockPos pos, Direction.Axis axis, int cut, boolean movePositive) {
        int value = coordinate(pos, axis);
        return movePositive ? value == cut : value == cut - 1;
    }

    private static int coordinate(BlockPos pos, Direction.Axis axis) {
        return switch (axis) {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
        };
    }

    private static String key(BlockPos pos, Direction face) {
        return pos.asLong() + "_" + face.get3DDataValue();
    }

    private record FacePatch(BlockPos pos, Direction face, String key) {}

    private PocketKnifeCutPreview() {}
}
