package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compression.CompressionBlacklist;
import com.misterblusky9.pocket.compression.CompressionSessions;
import com.misterblusky9.pocket.network.CompressionSyncPayload;
import com.misterblusky9.pocket.pocket.PocketMetrics;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ManualScaleOverride;
import com.misterblusky9.pocket.scale.ScaleCommandSource;
import com.misterblusky9.pocket.scale.ScaleState;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import java.util.UUID;

public final class PortableSubspaceCompressorBlockEntity extends KineticBlockEntity
        implements BlockEntitySubLevelActor, ScaleCommandSource {
    private static final float MIN_OPERATING_RPM = 1.0F;

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
            final BlockPos pos,
            final BlockState state
    ) {
        super(ModBlockEntities.PORTABLE_SUBSPACE_COMPRESSOR.get(), pos, state);
    }

    @Override
    public void sable$tick(final ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved() || subLevel.getUniqueId() == null) {
            resetAndRelease();
            return;
        }

        remember(subLevel);

        if (ManualScaleOverride.isSuspended(
                subLevel.getUniqueId(), subLevel.getLevel().getGameTime())) {
            this.commandedTarget = null;
            this.sealed = false;
            this.fieldActive = false;
            clearAcquisition();
            return;
        }

        this.operational = Math.abs(getSpeed()) >= MIN_OPERATING_RPM;
        this.desiredStage = stageForSignal(readRedstoneSignal());

        final UUID id = subLevel.getUniqueId();
        final boolean settled = ScaleState.isSettled(id);
        final CompressionStage current = ScaleState.getStage(subLevel);

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

            final boolean growing = this.desiredStage.depth() < current.depth();
            if (this.fieldActive && current != this.desiredStage && growing != this.lastFieldGrowing) {
                CompressionSyncPayload.sendMachineBegin(
                        subLevel,
                        this.worldPosition,
                        Math.max(1, this.acquisitionTicks),
                        growing
                );
                this.lastFieldGrowing = growing;
            }

            if (settled && current == this.desiredStage) finishJob();
            return;
        }

        if (!settled) return;

        if (current == this.desiredStage) {
            this.commandedTarget = null;
            releaseField();
            clearAcquisition();
            return;
        }

        if (this.desiredStage.depth() > current.depth()) {
            final long now = subLevel.getLevel().getGameTime();
            final CompressionBlacklist.Result blocked = CompressionBlacklist.find(subLevel, now);
            if (blocked.blocked()) {
                setJamMessage(blocked.message());
                releaseField();
                clearAcquisition();
                return;
            }

            final int blocks = PocketMetrics.measureForCompression(subLevel, now).blocks();
            if (blocks > PocketSized.MAX_COMPRESSED_BLOCKS) {
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
            final ServerSubLevel subLevel,
            final CompressionStage current,
            final CompressionStage target
    ) {
        releaseField();
        this.acquisitionTarget = target;
        this.commandedTarget = null;
        this.sealed = false;
        this.acquisitionAge = 0;
        this.acquisitionTicks = CompressionSessions.estimateAcquireTicks(subLevel);
        this.fieldActive = true;

        this.lastFieldGrowing = target.depth() < current.depth();
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

    public static CompressionStage stageForSignal(final int rawSignal) {
        final int signal = Math.max(0, Math.min(15, rawSignal));
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
    public Vector3d anchorLocalPoint() {
        return new Vector3d(
                this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + 0.5D,
                this.worldPosition.getZ() + 0.5D
        );
    }

    @Override
    public boolean tryConsumeTransition(
            final ServerSubLevel subLevel,
            final CompressionStage from,
            final CompressionStage to
    ) {
        if (!this.operational || !this.sealed || this.commandedTarget == null) return false;

        CompressionSyncPayload.sendPulse(subLevel, null);
        return true;
    }

    @Override
    public void onTransitionCompleted(
            final ServerSubLevel subLevel,
            final CompressionStage stage
    ) {
        if (this.commandedTarget != null && stage == this.commandedTarget) finishJob();
    }

    @Override
    public void setJamMessage(final String message) {
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

    private void remember(final ServerSubLevel subLevel) {
        this.activeSubLevel = subLevel;
        this.activeSubLevelId = subLevel.getUniqueId();
        if (subLevel.getLevel() instanceof final ServerLevel serverLevel) {
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
}
