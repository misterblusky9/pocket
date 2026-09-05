package com.misterblusky9.pocket.create;

import com.misterblusky9.pocket.client.SwitchBearingPartials;
import com.misterblusky9.pocket.client.SwitchBearingVisual;
import com.mojang.math.Axis;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ActorVisual;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.OrientedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Quaternionf;

public class SwitchBearingActorVisual extends ActorVisual {
    final OrientedInstance topInstance;
    final RotatingInstance shaft;

    final Direction facing;
    final Axis rotationAxis;
    final Quaternionf blockOrientation;

    public SwitchBearingActorVisual(
            final VisualizationContext visualizationContext,
            final VirtualRenderWorld simulationWorld,
            final MovementContext movementContext
    ) {
        super(visualizationContext, simulationWorld, movementContext);

        final BlockState blockState = movementContext.state;

        facing = blockState.getValue(BlockStateProperties.FACING);
        rotationAxis = Axis.of(Direction.get(Direction.AxisDirection.POSITIVE, facing.getAxis()).step());

        blockOrientation = SwitchBearingVisual.getBlockStateOrientation(facing);

        topInstance = instancerProvider
                .instancer(InstanceTypes.ORIENTED, Models.partial(SwitchBearingPartials.TOP))
                .createInstance();

        final int blockLight = localBlockLight();
        topInstance.position(movementContext.localPos)
                .rotation(blockOrientation)
                .light(blockLight, 0)
                .setChanged();

        shaft = instancerProvider
                .instancer(AllInstanceTypes.ROTATING, Models.partial(SwitchBearingPartials.SHAFT_HALF))
                .createInstance();

        final var axis = KineticBlockEntityVisual.rotationAxis(blockState);
        shaft.setRotationAxis(axis)
                .setRotationOffset(KineticBlockEntityVisual.rotationOffset(blockState, axis, movementContext.localPos))
                .setPosition(movementContext.localPos)
                .rotateToFace(Direction.SOUTH, blockState.getValue(BlockStateProperties.FACING).getOpposite())
                .light(blockLight, 0)
                .setChanged();
    }

    @Override
    public void beginFrame() {
        final float counterRotationAngle = SwitchBearingMovementBehaviour.getCounterRotationAngle(
                context, facing, AnimationTickHolder.getPartialTicks());

        final Quaternionf rotation = rotationAxis.rotationDegrees(counterRotationAngle);

        rotation.mul(blockOrientation);

        topInstance.rotation(rotation)
                .setChanged();
    }

    @Override
    protected void _delete() {
        topInstance.delete();
        shaft.delete();
    }
}
