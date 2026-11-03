package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.HatStandBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class HatPlateSlot extends ValueBoxTransform {
    public static final HatPlateSlot INSTANCE = new HatPlateSlot();

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Vector3d local = new Vector3d(0.0, 1.05 / 16.0, -3.5 / 16.0);
        new Quaterniond().rotateY(Math.toRadians(180.0 - facing(state).toYRot())).transform(local);
        return new Vec3(0.5 + local.x, local.y, 0.5 + local.z);
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack pose) {
        pose.mulPose(Axis.YP.rotationDegrees(AngleHelper.horizontalAngle(facing(state)) + 180.0F));
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
    }

    @Override
    public float getScale() {
        return 0.3F;
    }

    private static Direction facing(BlockState state) {
        return state.hasProperty(HatStandBlock.FACING) ? state.getValue(HatStandBlock.FACING) : Direction.NORTH;
    }

    private HatPlateSlot() {}
}
