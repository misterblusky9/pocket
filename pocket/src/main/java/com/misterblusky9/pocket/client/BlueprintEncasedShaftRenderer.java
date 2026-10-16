package com.misterblusky9.pocket.client;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedShaftBlock;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public final class BlueprintEncasedShaftRenderer extends KineticBlockEntityRenderer<KineticBlockEntity> {
    public BlueprintEncasedShaftRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(KineticBlockEntity be, BlockState state) {
        Direction facing = Direction.fromAxisAndDirection(
                state.getValue(EncasedShaftBlock.AXIS), Direction.AxisDirection.POSITIVE);
        return CachedBuffers.partialFacing(BlueprintEncasingClient.SHAFT, state, facing);
    }
}
