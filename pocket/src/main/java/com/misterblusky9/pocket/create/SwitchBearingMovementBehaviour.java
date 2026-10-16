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
    public ItemStack canBeDisabledVia(MovementContext context) {
        return null;
    }

    @Override
    public boolean disableBlockEntityRendering() {
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderInContraption(
            MovementContext context,
            VirtualRenderWorld renderWorld,
            ContraptionMatrices matrices,
            MultiBufferSource buffer
    ) {
        if (VisualizationManager.supportsVisualization(context.world)) {
            return;
        }

        Direction facing = context.state.getValue(BlockStateProperties.FACING);
        SuperByteBuffer superBuffer = CachedBuffers.partial(
                com.misterblusky9.pocket.client.SwitchBearingPartials.TOP, context.state);
        float renderPartialTicks = AnimationTickHolder.getPartialTicks();

        Quaternionf orientation = SwitchBearingVisual.getBlockStateOrientation(facing);

        float angle = getCounterRotationAngle(context, facing, renderPartialTicks)
                * facing.getAxisDirection().getStep();

        Quaternionf rotation = Axis.of(facing.step()).rotationDegrees(angle);

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
            VisualizationContext visualizationContext,
            VirtualRenderWorld simulationWorld,
            MovementContext movementContext
    ) {
        return new SwitchBearingActorVisual(visualizationContext, simulationWorld, movementContext);
    }

    static float getCounterRotationAngle(
            MovementContext context,
            Direction facing,
            float renderPartialTicks
    ) {
        if (!context.contraption.canBeStabilized(facing, context.localPos)) {
            return 0;
        }

        float offset = 0;
        Direction.Axis axis = facing.getAxis();
        AbstractContraptionEntity entity = context.contraption.entity;

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
