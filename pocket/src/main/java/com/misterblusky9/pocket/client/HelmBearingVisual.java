package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.HelmBearingBlockEntity;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.OrientedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Quaternionf;

import java.util.function.Consumer;

public final class HelmBearingVisual extends OrientedRotatingVisual<HelmBearingBlockEntity>
        implements SimpleDynamicVisual {
    private final OrientedInstance topInstance;
    private final Axis rotationAxis;
    private final Quaternionf blockOrientation;

    public HelmBearingVisual(
            final VisualizationContext context,
            final HelmBearingBlockEntity blockEntity,
            final float partialTick
    ) {
        super(
                context,
                blockEntity,
                partialTick,
                Direction.SOUTH,
                blockEntity.getBlockState().getValue(BlockStateProperties.FACING).getOpposite(),
                Models.partial(HelmBearingPartials.SHAFT_HALF)
        );

        final Direction facing = blockState.getValue(BlockStateProperties.FACING);
        rotationAxis = Axis.of(Direction.get(Direction.AxisDirection.POSITIVE, rotationAxis()).step());
        blockOrientation = SwitchBearingVisual.getBlockStateOrientation(facing);

        topInstance = instancerProvider()
                .instancer(InstanceTypes.ORIENTED, Models.partial(HelmBearingPartials.TOP))
                .createInstance();

        topInstance.position(getVisualPosition())
                .rotation(blockOrientation)
                .setChanged();
    }

    @Override
    public void beginFrame(final DynamicVisual.Context ctx) {
        final float interpolatedAngle = blockEntity.getInterpolatedAngle(ctx.partialTick() - 1);
        final Quaternionf rotation = rotationAxis.rotationDegrees(interpolatedAngle);
        rotation.mul(blockOrientation);
        topInstance.rotation(rotation).setChanged();
    }

    @Override
    public void updateLight(final float partialTick) {
        super.updateLight(partialTick);
        relight(topInstance);
    }

    @Override
    protected void _delete() {
        super._delete();
        topInstance.delete();
    }

    @Override
    public void collectCrumblingInstances(final Consumer<Instance> consumer) {
        super.collectCrumblingInstances(consumer);
        consumer.accept(topInstance);
    }
}
