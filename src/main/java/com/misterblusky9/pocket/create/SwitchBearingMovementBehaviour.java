package com.misterblusky9.pocket.create;

import com.misterblusky9.pocket.client.SwitchBearingVisual;
import com.mojang.math.Axis;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ActorVisual;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

public class SwitchBearingMovementBehaviour implements MovementBehaviour {
    @Override
    public ItemStack canBeDisabledVia(final MovementContext context) {
        return null;
    }

    @Override
    public boolean disableBlockEntityRendering() {
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderInContraption(
            final MovementContext context,
            final VirtualRenderWorld renderWorld,
            final ContraptionMatrices matrices,
            final MultiBufferSource buffer
    ) {
        if (VisualizationManager.supportsVisualization(context.world)) {
            return;
        }

        final Direction facing = context.state.getValue(BlockStateProperties.FACING);
        final SuperByteBuffer superBuffer = CachedBuffers.partial(
                com.misterblusky9.pocket.client.SwitchBearingPartials.TOP, context.state);
        final float renderPartialTicks = AnimationTickHolder.getPartialTicks();

        Quaternionf orientation = SwitchBearingVisual.getBlockStateOrientation(facing);

        final float angle = getCounterRotationAngle(context, facing, renderPartialTicks)
                * facing.getAxisDirection().getStep();

        final Quaternionf rotation = Axis.of(facing.step()).rotationDegrees(angle);

        rotation.mul(orientation);

        orientation = rotation;

        superBuffer.transform(matrices.getModel());
        superBuffer.rotateCentered(orientation);

        superBuffer.light(LevelRenderer.getLightColor(renderWorld, context.localPos))
                .useLevelLight(context.world, matrices.getWorld())
                .renderInto(matrices.getViewProjection(), buffer.getBuffer(RenderType.solid()));
    }

    @Nullable
    @Override
    public ActorVisual createVisual(
            final VisualizationContext visualizationContext,
            final VirtualRenderWorld simulationWorld,
            final MovementContext movementContext
    ) {
        return new SwitchBearingActorVisual(visualizationContext, simulationWorld, movementContext);
    }

    static float getCounterRotationAngle(
            final MovementContext context,
            final Direction facing,
            final float renderPartialTicks
    ) {
        if (!context.contraption.canBeStabilized(facing, context.localPos)) {
            return 0;
        }

        float offset = 0;
        final Direction.Axis axis = facing.getAxis();
        final AbstractContraptionEntity entity = context.contraption.entity;

        if (entity instanceof ControlledContraptionEntity controlledCE) {
            if (context.contraption.canBeStabilized(facing, context.localPos)) {
                offset = -controlledCE.getAngle(renderPartialTicks);
            }

        } else if (entity instanceof OrientedContraptionEntity orientedCE) {
            if (axis.isVertical()) {
                offset = -orientedCE.getViewYRot(renderPartialTicks);
            } else {
                if (orientedCE.isInitialOrientationPresent()
                        && orientedCE.getInitialOrientation().getAxis() == axis) {
                    offset = -orientedCE.getViewXRot(renderPartialTicks);
                }
            }
        }
        return offset;
    }
}
