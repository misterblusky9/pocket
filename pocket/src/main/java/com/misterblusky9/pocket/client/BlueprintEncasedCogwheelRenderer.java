package com.misterblusky9.pocket.client;

import com.simibubi.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogRenderer;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public final class BlueprintEncasedCogwheelRenderer extends EncasedCogRenderer {
    private final boolean large;

    private BlueprintEncasedCogwheelRenderer(BlockEntityRendererProvider.Context context, boolean large) {
        super(context, large);
        this.large = large;
    }

    public static BlueprintEncasedCogwheelRenderer small(BlockEntityRendererProvider.Context context) {
        return new BlueprintEncasedCogwheelRenderer(context, false);
    }

    public static BlueprintEncasedCogwheelRenderer large(BlockEntityRendererProvider.Context context) {
        return new BlueprintEncasedCogwheelRenderer(context, true);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(SimpleKineticBlockEntity be, BlockState state) {
        Direction facing = Direction.fromAxisAndDirection(
                state.getValue(EncasedCogwheelBlock.AXIS), Direction.AxisDirection.POSITIVE);
        return CachedBuffers.partialFacingVertical(
                large ? BlueprintEncasingClient.LARGE_COGWHEEL : BlueprintEncasingClient.COGWHEEL,
                state, facing);
    }
}
