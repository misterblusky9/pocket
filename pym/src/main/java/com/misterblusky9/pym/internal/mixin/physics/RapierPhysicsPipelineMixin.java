package com.misterblusky9.pym.internal.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.api.event.PhysicsSceneClosingEvent;
import com.misterblusky9.pym.internal.debug.PymTrace;
import com.misterblusky9.pym.internal.physics.CoupledMass;
import com.misterblusky9.pym.internal.physics.PlotShapeCache;
import com.misterblusky9.pym.internal.physics.RapierSceneLifetime;
import com.misterblusky9.pym.internal.physics.ColliderCoordinator;
import com.misterblusky9.pym.internal.physics.ScaledColliderRebuildQueue;
import com.misterblusky9.pym.internal.physics.ScaledFluidForces;
import com.misterblusky9.pym.internal.physics.ScalePhysicsTransitions;
import com.misterblusky9.pym.internal.physics.ScaledRebuildCollisionEffectFilter;
import com.misterblusky9.pym.internal.scale.ScaleState;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline", remap = false)
public abstract class RapierPhysicsPipelineMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow protected abstract long getSceneHandle();

    @Inject(method = "init", at = @At("RETURN"), remap = false)
    private void pym$recordSceneGeneration(
            final org.joml.Vector3dc gravity,
            final double timeScale,
            final CallbackInfo ci
    ) {
        RapierSceneLifetime.opened(this.level, this.getSceneHandle());
    }

    @Inject(method = "dispose", at = @At("HEAD"), remap = false)
    private void pym$invalidateSceneGenerationBeforeNativeFree(final CallbackInfo ci) {
        ScaledRebuildCollisionEffectFilter.forgetScene(this.getSceneHandle());
        NeoForge.EVENT_BUS.post(new PhysicsSceneClosingEvent(this.level));
        CoupledMass.invalidate();
        RapierSceneLifetime.closing(this.level);
    }

    @WrapOperation(
            method = "processCollisionEffects",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;clearCollisions(J)[D"
            ),
            remap = false
    )
    private double[] pym$filterScaledRebuildCollisionEffects(
            final long scene,
            final Operation<double[]> original
    ) {
        final double[] collisions = original.call(scene);
        return ScaledRebuildCollisionEffectFilter.filter(scene, this.level.getGameTime(), collisions);
    }

    @WrapOperation(
            method = "prePhysicsTicks",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;tick(JD)V"
            ),
            remap = false
    )
    private void pym$replaceNativeScaledFluidPass(
            final long scene,
            final double timeStep,
            final Operation<Void> original
    ) {
        final List<ServerSubLevel> suppressed = ScaledFluidForces.suppressNativePass(this.level);
        try {
            original.call(scene, timeStep);
        } finally {
            ScaledFluidForces.restoreNativePass(suppressed);
        }
    }

    @Inject(method = "handleChunkSectionAddition", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$ignoreDetailedSectionAdditionWhileScaled(
            final LevelChunkSection section,
            final int x,
            final int y,
            final int z,
            final boolean uploadDataIfGlobal,
            final CallbackInfo ci
    ) {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        if (container == null) return;
        final LevelPlot plot = container.getPlot(x, z);
        if (plot == null) return;

        PlotShapeCache.invalidate(plot.getSubLevel());

        if (plot.getSubLevel() instanceof final ServerSubLevel serverSubLevel
                && ScalePhysicsTransitions.ownsScaledCollider(serverSubLevel)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleBlockChange", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$ignoreDetailedBlockChangesWhileScaled(
            final SectionPos sectionPos,
            final LevelChunkSection chunk,
            final int localX,
            final int localY,
            final int localZ,
            final BlockState oldState,
            final BlockState newState,
            final CallbackInfo ci
    ) {
        final int x = (sectionPos.x() << 4) + localX;
        final int y = (sectionPos.y() << 4) + localY;
        final int z = (sectionPos.z() << 4) + localZ;
        final BlockPos pos = new BlockPos(x, y, z);
        final SubLevel subLevel = Sable.HELPER.getContaining(this.level, pos);

        final boolean blockTypeChanged = oldState.getBlock() != newState.getBlock();
        if (blockTypeChanged || pym$collisionChanged(pos, oldState, newState)) {
            PlotShapeCache.invalidateBlock(subLevel, x, y, z);
        }

        if (subLevel instanceof final ServerSubLevel serverSubLevel
                && ScalePhysicsTransitions.ownsScaledCollider(serverSubLevel)) {
            ci.cancel();
        }
    }

    @Unique
    private boolean pym$collisionChanged(
            final BlockPos pos,
            final BlockState oldState,
            final BlockState newState
    ) {
        if (oldState == newState) return false;

        final VoxelShape oldShape = oldState.getCollisionShape(this.level, pos);
        final VoxelShape newShape = newState.getCollisionShape(this.level, pos);
        if (oldShape.isEmpty() && newShape.isEmpty()) return false;

        return Shapes.joinIsNotEmpty(oldShape, newShape, BooleanOp.NOT_SAME);
    }

    @Inject(method = "handleChunkSectionRemoval", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$keepSyntheticColliderDuringPlotSectionChanges(
            final int x,
            final int y,
            final int z,
            final CallbackInfo ci
    ) {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        if (container == null) return;
        final LevelPlot plot = container.getPlot(x, z);
        if (plot == null) return;

        PlotShapeCache.invalidate(plot.getSubLevel());

        if (plot.getSubLevel() instanceof final ServerSubLevel serverSubLevel
                && ScalePhysicsTransitions.ownsScaledCollider(serverSubLevel)) {
            ci.cancel();
        }
    }

    @Inject(method = "onStatsChanged", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$replaceScaledStatsUpdate(
            final ServerSubLevel subLevel,
            final CallbackInfo ci
    ) {
        if (!ScalePhysicsTransitions.ownsScaledCollider(subLevel)) return;

        final boolean firstDirty = ScaledColliderRebuildQueue.mark(subLevel);
        if (firstDirty) {
            PymTrace.scale(
                    "onStatsChanged scaled {} collisionScale={} loadedChunks={} hasTrackedState={}",
                    PymTrace.context(subLevel),
                    ScaleState.getServerScale(subLevel),
                    subLevel.getPlot().getLoadedChunks().size(),
                    ScaleState.hasServerState(subLevel.getUniqueId()));
        }

        ScalePhysicsTransitions.syncExternalStats(subLevel);
        ColliderCoordinator.applyScaledLocalBounds(subLevel);
        ci.cancel();
    }

}
