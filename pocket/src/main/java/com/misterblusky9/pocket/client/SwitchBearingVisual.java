package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.SwitchBearingBlockEntity;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.OrientedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Quaternionf;

import java.util.function.Consumer;

public class SwitchBearingVisual extends OrientedRotatingVisual<SwitchBearingBlockEntity>
        implements SimpleDynamicVisual {
    final OrientedInstance topInstance;

    final Axis rotationAxis;
    final Quaternionf blockOrientation;

    public SwitchBearingVisual(
            final VisualizationContext context,
            final SwitchBearingBlockEntity blockEntity,
            final float partialTick
    ) {
        super(context, blockEntity, partialTick, Direction.SOUTH,
                blockEntity.getBlockState().getValue(BlockStateProperties.FACING).getOpposite(),
                Models.partial(SwitchBearingPartials.SHAFT_HALF));

        final Direction facing = blockState.getValue(BlockStateProperties.FACING);
        rotationAxis = Axis.of(Direction.get(Direction.AxisDirection.POSITIVE, rotationAxis()).step());

        blockOrientation = getBlockStateOrientation(facing);

        topInstance = instancerProvider()
                .instancer(InstanceTypes.ORIENTED, Models.partial(SwitchBearingPartials.TOP))
                .createInstance();

        topInstance.position(getVisualPosition())
                .rotation(blockOrientation)
                .setChanged();
    }

    @Override
    public void beginFrame(final DynamicVisual.Context ctx) {
        final float interpolatedAngle = blockEntity.getInterpolatedAngle(ctx.partialTick() - 1);
        final Quaternionf rot = rotationAxis.rotationDegrees(interpolatedAngle);

        rot.mul(blockOrientation);

        topInstance.rotation(rot)
                .setChanged();
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

    public static Quaternionf getBlockStateOrientation(final Direction facing) {
        final Quaternionf orientation;

        if (facing.getAxis().isHorizontal()) {
            orientation = Axis.YP.rotationDegrees(AngleHelper.horizontalAngle(facing.getOpposite()));
        } else {
            orientation = new Quaternionf();
        }

        orientation.mul(Axis.XP.rotationDegrees(-90 - AngleHelper.verticalAngle(facing)));
        return orientation;
    }

    @Override
    public void collectCrumblingInstances(final Consumer<Instance> consumer) {
        super.collectCrumblingInstances(consumer);
        consumer.accept(topInstance);
    }
}
