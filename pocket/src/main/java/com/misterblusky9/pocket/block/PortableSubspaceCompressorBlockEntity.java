package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.config.DeviceRanges;
import com.misterblusky9.pocket.scale.ResizeFeedback;
import com.misterblusky9.pym.api.PlotContents;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compression.CompressionSessions;
import com.misterblusky9.pocket.network.CompressionSyncPayload;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ScaleCommandSource;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PortableSubspaceCompressorBlockEntity extends KineticBlockEntity
        implements BlockEntitySubLevelActor, ScaleCommandSource {
    private static final float MIN_OPERATING_RPM = 1.0F;

    private enum LockMode implements INamedIconOptions {
        UNLOCKED(AllIcons.I_CONFIG_UNLOCKED),
        LOCKED(AllIcons.I_CONFIG_LOCKED);

        private final AllIcons icon;

        LockMode(AllIcons icon) {
            this.icon = icon;
        }

        @Override
        public AllIcons getIcon() {
            return this.icon;
        }

        @Override
        public String getTranslationKey() {
            return "pocket.portable_subspace_compressor.lock." + name().toLowerCase(Locale.ROOT);
        }
    }

    private ScrollOptionBehaviour<LockMode> lock;
    private CompressionStage desiredStage = CompressionStage.NORMAL;
    private CompressionStage acquisitionTarget;
    private CompressionStage commandedTarget;

    private int acquisitionAge;
    private int acquisitionTicks;
    private boolean sealed;
    private boolean fieldActive;
    private boolean operational;
    private boolean lastFieldGrowing;

    private UUID activeSubLevelId;
    private ServerLevel activeServerLevel;
    private ServerSubLevel activeSubLevel;
    private String jamMessage = "";

    public PortableSubspaceCompressorBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        super(ModBlockEntities.PORTABLE_SUBSPACE_COMPRESSOR.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        this.lock = new ScrollOptionBehaviour<>(
                LockMode.class,
                Component.translatable("pocket.portable_subspace_compressor.lock"),
                this,
                new TopValueBoxTransform()
        );
        behaviours.add(this.lock);
    }

    public boolean blocksExternalResize() {
        return this.lock != null && this.lock.get() == LockMode.LOCKED
                && !isRemoved() && this.level != null && Math.abs(getSpeed()) >= MIN_OPERATING_RPM;
    }

    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved() || subLevel.getUniqueId() == null) {
            resetAndRelease();
            return;
        }

        remember(subLevel);

        if (Pym.resize().driversSuspended(
                subLevel.getUniqueId(), subLevel.getLevel().getGameTime())) {
            this.commandedTarget = null;
            this.sealed = false;
            this.fieldActive = false;
            clearAcquisition();
            return;
        }

        this.operational = Math.abs(getSpeed()) >= MIN_OPERATING_RPM;
        this.desiredStage = stageForSignal(readRedstoneSignal());

        UUID id = subLevel.getUniqueId();
        boolean settled = Pym.scale().isSettled(id);
        double current = Pym.scale().settled(subLevel);

        if (!this.operational) {
            this.commandedTarget = null;
            this.sealed = false;
            this.acquisitionTarget = null;
            this.acquisitionAge = 0;
            if (settled) releaseField();
            return;
        }

        if (!settled && this.commandedTarget == null) {
            releaseField();
            clearAcquisition();
            return;
        }

        if (this.sealed) {
            this.commandedTarget = this.desiredStage;

            boolean growing = this.desiredStage.scale() > current;
            if (this.fieldActive && !Pym.scale().isAt(subLevel, this.desiredStage.scale()) && growing != this.lastFieldGrowing) {
                CompressionSyncPayload.sendMachineBegin(
                        subLevel,
                        this.worldPosition,
                        Math.max(1, this.acquisitionTicks),
                        growing
                );
                this.lastFieldGrowing = growing;
            }

            if (settled && Pym.scale().isAt(subLevel, this.desiredStage.scale())) finishJob();
            return;
        }

        if (!settled) return;

        if (Pym.scale().isAt(subLevel, this.desiredStage.scale())) {
            this.commandedTarget = null;
            releaseField();
            clearAcquisition();
            return;
        }

        if (this.desiredStage.scale() < current) {
            PlotContents contents = Pym.resize().contents(subLevel);
            String blocked = ResizeFeedback.cannotShrink(contents);
            if (blocked != null) {
                setJamMessage(blocked);
                releaseField();
                clearAcquisition();
                return;
            }

            int blocks = contents.blocks();
            if (blocks > Pym.resize().shrunkBlockLimit()) {
                setJamMessage("Hard limit exceeded: " + blocks + " blocks");
                releaseField();
                clearAcquisition();
                return;
            }
        }

        clearJamMessage();

        if (this.acquisitionTarget != this.desiredStage || !this.fieldActive) {
            beginAcquisition(subLevel, current, this.desiredStage);
            return;
        }

        this.acquisitionAge++;
        if (this.acquisitionAge < this.acquisitionTicks) return;

        this.sealed = true;
        this.commandedTarget = this.desiredStage;
    }

    private void beginAcquisition(
            ServerSubLevel subLevel,
            double current,
            CompressionStage target
    ) {
        releaseField();
        this.acquisitionTarget = target;
        this.commandedTarget = null;
        this.sealed = false;
        this.acquisitionAge = 0;
        this.acquisitionTicks = CompressionSessions.estimateAcquireTicks(subLevel);
        this.fieldActive = true;

        this.lastFieldGrowing = target.scale() > current;
        CompressionSyncPayload.sendMachineBegin(
                subLevel,
                this.worldPosition,
                this.acquisitionTicks,
                this.lastFieldGrowing
        );
    }

    private int readRedstoneSignal() {
        if (this.level == null) return 0;
        return Math.max(0, Math.min(15, this.level.getBestNeighborSignal(this.worldPosition)));
    }

    public static CompressionStage stageForSignal(int rawSignal) {
        int signal = Math.max(0, Math.min(15, rawSignal));
        if (signal == 0) return CompressionStage.NORMAL;
        if (signal <= 3) return CompressionStage.HALF;
        if (signal <= 6) return CompressionStage.QUARTER;
        if (signal <= 10) return CompressionStage.EIGHTH;
        return CompressionStage.SIXTEENTH;
    }

    @Override
    public CompressionStage commandedStage() {
        if (!this.operational || !this.sealed) return null;
        return this.commandedTarget;
    }

    @Override
    public boolean stepwiseTransitions() {
        return true;
    }

    @Override
    public boolean propagatesJoints() {
        return true;
    }

    @Override
    public ScaleBounds bounds() {
        return DeviceRanges.of(DeviceRanges.Device.COMPRESSORS);
    }

    @Override
    public Vector3d anchorLocalPoint() {
        return new Vector3d(
                this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + 0.5D,
                this.worldPosition.getZ() + 0.5D
        );
    }

    @Override
    public boolean tryConsumeTransition(
            ServerSubLevel subLevel,
            CompressionStage from,
            CompressionStage to
    ) {
        if (!this.operational || !this.sealed || this.commandedTarget == null) return false;

        CompressionSyncPayload.sendPulse(subLevel, null);
        return true;
    }

    @Override
    public String transitionRefusedMessage() {
        if (!this.operational) return "Compressor needs rotation";
        if (!this.sealed) return "Compressor is not sealed";
        return null;
    }

    @Override
    public void onTransitionCompleted(
            ServerSubLevel subLevel,
            CompressionStage stage
    ) {
        if (this.commandedTarget != null && stage == this.commandedTarget) finishJob();
    }

    @Override
    public void setJamMessage(String message) {
        this.jamMessage = message == null ? "" : message;
    }

    @Override
    public void clearJamMessage() {
        this.jamMessage = "";
    }

    public String jamMessage() {
        return this.jamMessage;
    }

    @Override
    public void remove() {
        resetAndRelease();
        super.remove();
    }

    @Override
    public void destroy() {
        resetAndRelease();
        super.destroy();
    }

    private void finishJob() {
        this.commandedTarget = null;
        this.sealed = false;
        releaseField();
        clearAcquisition();
    }

    private void clearAcquisition() {
        this.acquisitionTarget = null;
        this.acquisitionAge = 0;
        this.acquisitionTicks = 0;
    }

    private void remember(ServerSubLevel subLevel) {
        this.activeSubLevel = subLevel;
        this.activeSubLevelId = subLevel.getUniqueId();
        if (subLevel.getLevel() instanceof ServerLevel serverLevel) {
            this.activeServerLevel = serverLevel;
        }
    }

    private void resetAndRelease() {
        this.operational = false;
        this.commandedTarget = null;
        this.sealed = false;
        releaseField();
        clearAcquisition();
    }

    private void releaseField() {
        if (!this.fieldActive) return;

        if (this.activeSubLevel != null && !this.activeSubLevel.isRemoved()) {
            CompressionSyncPayload.sendRelease(this.activeSubLevel);
        } else if (this.activeServerLevel != null && this.activeSubLevelId != null) {
            CompressionSyncPayload.sendRelease(this.activeServerLevel, this.activeSubLevelId);
        }

        this.fieldActive = false;
    }

    private static final class TopValueBoxTransform extends ValueBoxTransform {
        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            float yRot = AngleHelper.horizontalAngle(state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING));
            return VecHelper.rotateCentered(VecHelper.voxelSpace(8, 13.5, 7.4), yRot, Direction.Axis.Y);
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            float yRot = AngleHelper.horizontalAngle(state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING));
            TransformStack.of(ms).rotateYDegrees(yRot).rotateXDegrees(90).translate(0, 0.1, 0);
        }
    }
}
