package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.compression.BeamSession;
import com.misterblusky9.pocket.compression.MachineBeamDriver;
import com.misterblusky9.pocket.config.DeviceRanges;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import dev.simulated_team.simulated.content.blocks.lasers.LaserBehaviour;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class StaticSubspaceCompressorBlockEntity extends KineticBlockEntity
        implements MachineBeamDriver.Host {
    public static final int MAX_SIGNAL = 15;

    private static final int CHARGE_TICKS = 30;
    private static final int ACQUIRE_SLOWDOWN = 3;
    private static final double TRANSITION_SPEED_FACTOR = 0.35D;

    private final MachineBeamDriver driver = new MachineBeamDriver(this);

    private LaserBehaviour laser;
    private ScrollOptionBehaviour<CompressorMode> mode;

    private int chargeAge;
    private boolean powered;
    private int signal;

    public StaticSubspaceCompressorBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState state
    ) {
        super(type, pos, state);
    }

    public StaticSubspaceCompressorBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.STATIC_SUBSPACE_COMPRESSOR.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        this.laser = new LaserBehaviour(this, this::gatherStartAndEnd, this::getRaycastLength);
        this.laser.setBlockCollide(ClipContext.Block.COLLIDER);
        this.laser.setFluidCollide(ClipContext.Fluid.NONE);
        this.laser.setShouldCast(this::shouldCast);
        behaviours.add(this.laser);

        this.mode = new ScrollOptionBehaviour<>(
                CompressorMode.class,
                Component.translatable("pocket.static_subspace_compressor.mode"),
                this,
                new CenteredSideValueBoxTransform((state, side) -> {
                    Direction facing = state.getValue(StaticSubspaceCompressorBlock.FACING);
                    return side != facing && side != facing.getOpposite();
                })
        );
        this.mode.withCallback(value -> {
            if (this.level != null && !this.level.isClientSide) reset(true);
        });
        behaviours.add(this.mode);
    }

    public CompressorMode mode() {
        return this.mode == null ? CompressorMode.SHRINK : this.mode.get();
    }

    private CompressionStage targetStage() {
        return mode().target();
    }

    public Direction getDirection() {
        BlockState state = getBlockState();
        return state.getBlock() instanceof StaticSubspaceCompressorBlock
                ? state.getValue(StaticSubspaceCompressorBlock.FACING)
                : Direction.NORTH;
    }

    public Vec3i getNormal() {
        return getDirection().getNormal();
    }

    public Couple<Vec3> gatherStartAndEnd() {
        Vec3i normal = getNormal();
        Vec3 start = Vec3.atCenterOf(this.worldPosition)
                .add(Vec3.atLowerCornerOf(normal).scale(0.5F));
        Vec3 end = start.add(
                Vec3.atLowerCornerOf(normal).scale(getRaycastLength())
        );
        return Couple.create(start, end);
    }

    @Override
    public AABB getRenderBoundingBox() {
        int range = (int) getRaycastLength();
        Vec3i normal = getNormal();
        return new AABB(getBlockPos())
                .expandTowards(Vec3.atLowerCornerOf(normal.multiply(range)));
    }

    public float getRaycastLength() {
        return this.signal;
    }

    private int redstoneSignal() {
        if (this.level == null) return 0;
        return Math.max(0, Math.min(MAX_SIGNAL, this.level.getBestNeighborSignal(this.worldPosition)));
    }

    public boolean shouldCast() {
        return this.powered;
    }

    @Override
    public void tick() {
        if (this.level == null) return;

        this.signal = redstoneSignal();
        this.powered = this.signal > 0
                && isSpeedRequirementFulfilled()
                && Math.abs(getSpeed()) > 0.0F;

        super.tick();
        if (!(this.level instanceof ServerLevel level)) return;

        if (!this.powered) {
            reset(true);
            return;
        }

        if (Pym.resize().driversSuspended(this.driver.targetId(), level.getGameTime())) {
            reset(false);
            return;
        }

        if (this.chargeAge < CHARGE_TICKS) {
            this.chargeAge++;
            return;
        }

        if (!this.driver.tick(level)) reset(false);
    }

    private void reset(boolean releaseVisuals) {
        this.chargeAge = 0;
        this.driver.clearTarget(releaseVisuals);
    }

    public boolean isEngaged() {
        return this.driver.isEngaged();
    }

    @Override
    public Level hostLevel() {
        return this.level;
    }

    @Override
    public BlockPos hostPos() {
        return this.worldPosition;
    }

    @Override
    public LaserBehaviour laser() {
        return this.laser;
    }

    @Override
    public Couple<Vec3> beam() {
        return gatherStartAndEnd();
    }

    @Override
    public float beamLength() {
        return getRaycastLength();
    }

    @Override
    public double goalScale() {
        return targetStage().scale();
    }

    @Override
    public ScaleBounds bounds() {
        return DeviceRanges.of(DeviceRanges.Device.COMPRESSORS);
    }

    @Override
    public double transitionTicks() {
        return Pym.resize().defaultTransitionTicks() / TRANSITION_SPEED_FACTOR;
    }

    @Override
    public BeamSession.Pace pace() {
        return BeamSession.Pace.STATIC_COMPRESSOR;
    }

    @Override
    public int acquireSlowdown() {
        return ACQUIRE_SLOWDOWN;
    }

    @Override
    public boolean hostRemoved() {
        return isRemoved();
    }

    @Override
    public void remove() {
        reset(true);
        super.remove();
    }

    @Override
    public void destroy() {
        reset(true);
        super.destroy();
    }
}
