package com.misterblusky9.pocket.block;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.IBearingBlockEntity;
import com.misterblusky9.pocket.create.SwitchBearingContraption;
import com.misterblusky9.pocket.debug.SwitchBearingDebug;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.ServerSpeedProvider;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

public class SwitchBearingBlockEntity extends GeneratingKineticBlockEntity
        implements IBearingBlockEntity, IDisplayAssemblyExceptions, SwitchControllerBlockEntity {
    protected SwitchModeBehaviour switchMode;
    protected ControlledContraptionEntity movedContraption;
    protected float angle;
    protected boolean running;
    protected boolean assembleNextTick;
    protected float clientAngleDiff;
    protected AssemblyException lastException;
    protected double sequencedAngleLimit;

    private float prevAngle;
    private static final int ANALOG_SETTLE_TICKS = 15;

    private int pulseTicks;
    private int power;
    private int analogChangeTimer;

    public SwitchBearingBlockEntity(final BlockPos pos, final BlockState state) {
        super(ModBlockEntities.SWITCH_BEARING.get(), pos, state);
        setLazyTickRate(3);
        sequencedAngleLimit = -1;
    }

    @Override
    public boolean isWoodenTop() {
        return false;
    }

    @Override
    protected boolean syncSequenceContext() {
        return true;
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        switchMode = new SwitchModeBehaviour(
                Component.translatable("pocket.switch.control_mode"), this, getMovementModeSlot());
        switchMode.withCallback(value -> onSwitchModeChanged());
        behaviours.add(switchMode);
        registerAwardables(behaviours, AllAdvancements.CONTRAPTION_ACTORS);
    }

    @Override
    public void remove() {
        if (!level.isClientSide) {
            disassemble();
        }
        super.remove();
    }

    @Override
    public void write(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket) {
        compound.putBoolean("Running", running);
        if (pulseTicks > 0) {
            compound.putInt("PulseTicks", pulseTicks);
        }
        compound.putInt("Power", power);
        if (analogChangeTimer > 0) {
            compound.putInt("AnalogChangeTimer", analogChangeTimer);
        }
        compound.putFloat("Angle", angle);
        if (sequencedAngleLimit >= 0) {
            compound.putDouble("SequencedAngleLimit", sequencedAngleLimit);
        }
        AssemblyException.write(compound, registries, lastException);
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(
            final CompoundTag compound,
            final HolderLookup.Provider registries,
            final boolean clientPacket
    ) {
        if (wasMoved) {
            super.read(compound, registries, clientPacket);
            return;
        }

        final float angleBefore = angle;
        running = compound.getBoolean("Running");
        pulseTicks = compound.getInt("PulseTicks");
        power = compound.getInt("Power");
        analogChangeTimer = compound.getInt("AnalogChangeTimer");
        angle = compound.getFloat("Angle");
        sequencedAngleLimit = compound.contains("SequencedAngleLimit")
                ? compound.getDouble("SequencedAngleLimit")
                : -1;
        lastException = AssemblyException.read(compound, registries);
        super.read(compound, registries, clientPacket);
        if (!clientPacket) {
            return;
        }
        if (running) {
            if (movedContraption == null || !movedContraption.isStalled()) {
                clientAngleDiff = AngleHelper.getShortestAngleDiff(angleBefore, angle);
                angle = angleBefore;
            }
        } else {
            movedContraption = null;
        }
    }

    @Override
    public float getInterpolatedAngle(float partialTicks) {
        if (isVirtual()) {
            return Mth.lerp(partialTicks + .5f, prevAngle, angle);
        }
        if (movedContraption == null || movedContraption.isStalled() || !running) {
            partialTicks = 0;
        }
        float angularSpeed = getAngularSpeed();
        if (sequencedAngleLimit >= 0) {
            angularSpeed = (float) Mth.clamp(angularSpeed, -sequencedAngleLimit, sequencedAngleLimit);
        }
        return Mth.lerp(partialTicks, angle, angle + angularSpeed);
    }

    @Override
    public void onSpeedChanged(final float prevSpeed) {
        super.onSpeedChanged(prevSpeed);
        assembleNextTick = true;
        sequencedAngleLimit = -1;

        if (movedContraption != null && Math.signum(prevSpeed) != Math.signum(getSpeed()) && prevSpeed != 0) {
            if (!movedContraption.isStalled()) {
                angle = Math.round(angle);
                applyRotation();
            }
            movedContraption.getContraption().stop(level);
        }

        if (sequenceContext != null && sequenceContext.instruction() == SequencerInstructions.TURN_ANGLE) {
            sequencedAngleLimit = sequenceContext.getEffectiveValue(getTheoreticalSpeed());
        }
    }

    public float getAngularSpeed() {
        float speed = convertToAngular(getSpeed());
        if (getSpeed() == 0) {
            speed = 0;
        }
        if (level.isClientSide) {
            speed *= ServerSpeedProvider.get();
            speed += clientAngleDiff / 3f;
        }
        return speed;
    }

    @Override
    public AssemblyException getLastAssemblyException() {
        return lastException;
    }

    @Override
    public BlockPos getBlockPosition() {
        return worldPosition;
    }

    public void assemble() {
        if (!(level.getBlockState(worldPosition).getBlock() instanceof SwitchBearingBlock)) {
            return;
        }

        final Direction direction = getBlockState().getValue(SwitchBearingBlock.FACING);
        SwitchBearingDebug.info(
                "Assembly requested controller={} facing={} speed={}",
                worldPosition, direction, getSpeed()
        );

        final SwitchBearingContraption contraption = new SwitchBearingContraption(direction);
        try {
            if (!contraption.assemble(level, worldPosition)) {
                SwitchBearingDebug.info("Assembly produced no contraption controller={}", worldPosition);
                return;
            }

            lastException = null;
        } catch (final AssemblyException e) {
            lastException = e;
            SwitchBearingDebug.warn("Assembly failed controller={} error={}", worldPosition, e.getMessage());
            sendData();
            return;
        }

        SwitchBearingDebug.info(
                "Assembly complete controller={} blocks={} interactionBounds={} type={}",
                worldPosition,
                contraption.getBlocks().size(),
                contraption.getInteractionBounds(),
                contraption.getClass().getSimpleName()
        );

        contraption.removeBlocksFromWorld(level, BlockPos.ZERO);
        movedContraption = ControlledContraptionEntity.create(level, this, contraption);
        final BlockPos anchor = worldPosition.relative(direction);
        movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        movedContraption.setRotationAxis(direction.getAxis());
        level.addFreshEntity(movedContraption);

        SwitchBearingDebug.info(
                "Spawned controlled switch contraption controller={} entityId={} anchor={} axis={}",
                worldPosition,
                movedContraption.getId(),
                anchor,
                direction.getAxis()
        );

        AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, worldPosition);

        if (contraption.containsBlockBreakers()) {
            award(AllAdvancements.CONTRAPTION_ACTORS);
        }

        running = true;
        angle = 0;
        sendData();
        updateGeneratedRotation();
    }

    public void disassemble() {
        if (!running && movedContraption == null) {
            return;
        }
        angle = 0;
        sequencedAngleLimit = -1;
        if (movedContraption != null) {
            movedContraption.disassemble();
            AllSoundEvents.CONTRAPTION_DISASSEMBLE.playOnServer(level, worldPosition);
        }

        movedContraption = null;
        running = false;
        updateGeneratedRotation();
        assembleNextTick = false;
        sendData();
    }

    @Override
    public void tick() {
        tickPulse();
        tickAnalogRelease();
        super.tick();

        prevAngle = angle;
        if (level.isClientSide) {
            clientAngleDiff /= 2;
        }

        if (!level.isClientSide && assembleNextTick) {
            assembleNextTick = false;

            if (running) {
                if (movedContraption == null
                        || movedContraption.getContraption().getBlocks().isEmpty()) {
                    SwitchBearingDebug.info(
                            "Running switch contraption became invalid; disassembling controller={} speed={}",
                            worldPosition,
                            getSpeed()
                    );
                    disassemble();
                    return;
                }

                if (getSpeed() == 0) {
                    SwitchBearingDebug.info(
                            "Switch contraption retained at 0 RPM controller={} entityId={}",
                            worldPosition,
                            movedContraption.getId()
                    );
                }
            } else {
                if (getSpeed() == 0) {
                    SwitchBearingDebug.info(
                            "Zero-RPM assembly accepted controller={}",
                            worldPosition
                    );
                }
                assemble();
            }
        }

        if (!running) {
            return;
        }

        if (!(movedContraption != null && movedContraption.isStalled())) {
            float angularSpeed = getAngularSpeed();
            if (sequencedAngleLimit >= 0) {
                angularSpeed = (float) Mth.clamp(angularSpeed, -sequencedAngleLimit, sequencedAngleLimit);
                sequencedAngleLimit = Math.max(0, sequencedAngleLimit - Math.abs(angularSpeed));
            }
            final float newAngle = angle + angularSpeed;
            angle = (float) (newAngle % 360);
        }

        applyRotation();
    }

    private void tickPulse() {
        if (level == null || level.isClientSide || pulseTicks <= 0) {
            return;
        }
        if (--pulseTicks > 0) {
            return;
        }

        setPower(0, false);
        click(0.3F, 0.5F);
        level.gameEvent(null, GameEvent.BLOCK_DEACTIVATE, worldPosition);
    }

    private void setPower(final int strength, final boolean deferNeighbours) {
        final int clamped = Mth.clamp(strength, 0, 15);
        if (clamped == power) {
            return;
        }
        power = clamped;
        setChanged();

        final BlockState state = level.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof SwitchBearingBlock) || !state.hasProperty(SwitchBearingBlock.POWERED)) {
            return;
        }

        final boolean lit = power > 0;
        BlockState updated = state;
        if (state.getValue(SwitchBearingBlock.POWERED) != lit) {
            updated = state.setValue(SwitchBearingBlock.POWERED, lit);
            level.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS);
        }

        if (!deferNeighbours) {
            SwitchBearingBlock.updateRedstoneNeighbours(updated, level, worldPosition);
        }
    }

    @Override
    public boolean addToGoggleTooltip(final List<Component> tooltip, final boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        if (switchMode != null && switchMode.get() == SwitchMode.ANALOG) {
            CreateLang.translate("tooltip.analogStrength", power).forGoggles(tooltip);
            added = true;
        }
        return added;
    }

    @Override
    public int getRedstonePower() {
        return power;
    }

    private void click(final float volume, final float pitch) {
        level.playSound(
                null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, volume, pitch);
    }

    private void changeAnalogState(final boolean back) {
        final int previous = power;
        setPower(power + (back ? -1 : 1), true);
        if (previous != power) {
            analogChangeTimer = ANALOG_SETTLE_TICKS;
        }
        click(0.2F, 0.25F + ((power + 5) / 15F) * 0.5F);
        sendData();
    }

    private void tickAnalogRelease() {
        if (level == null || level.isClientSide || analogChangeTimer <= 0) {
            return;
        }
        if (--analogChangeTimer > 0) {
            return;
        }
        final BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() instanceof SwitchBearingBlock) {
            SwitchBearingBlock.updateRedstoneNeighbours(state, level, worldPosition);
        }
    }

    private void onSwitchModeChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        pulseTicks = 0;
        analogChangeTimer = 0;
        setPower(0, false);
    }

    public boolean isNearInitialAngle() {
        return Math.abs(angle) < 22.5 || Math.abs(angle) > 360 - 22.5;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (movedContraption != null && !level.isClientSide) {
            sendData();
        }
    }

    protected void applyRotation() {
        if (movedContraption == null) {
            return;
        }
        movedContraption.setAngle(angle);
        final BlockState blockState = getBlockState();
        if (blockState.hasProperty(BlockStateProperties.FACING)) {
            movedContraption.setRotationAxis(blockState.getValue(BlockStateProperties.FACING).getAxis());
        }
    }

    @Override
    public void attach(final ControlledContraptionEntity contraption) {
        final BlockState blockState = getBlockState();
        if (!(contraption.getContraption() instanceof BearingContraption)) {
            return;
        }
        if (!blockState.hasProperty(SwitchBearingBlock.FACING)) {
            return;
        }

        this.movedContraption = contraption;
        setChanged();
        final BlockPos anchor = worldPosition.relative(blockState.getValue(SwitchBearingBlock.FACING));
        movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        if (!level.isClientSide) {
            this.running = true;
            sendData();
        }
    }

    @Override
    public void onStall() {
        if (!level.isClientSide) {
            sendData();
        }
    }

    @Override
    public boolean isValid() {
        return !isRemoved();
    }

    @Override
    public boolean isAttachedTo(final AbstractContraptionEntity contraption) {
        return movedContraption == contraption;
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean addToTooltip(final List<Component> tooltip, final boolean isPlayerSneaking) {
        if (super.addToTooltip(tooltip, isPlayerSneaking)) {
            return true;
        }
        if (isPlayerSneaking) {
            return false;
        }
        if (getSpeed() == 0) {
            return false;
        }
        if (running) {
            return false;
        }
        final BlockState state = getBlockState();
        if (!(state.getBlock() instanceof SwitchBearingBlock)) {
            return false;
        }

        final BlockState attachedState =
                level.getBlockState(worldPosition.relative(state.getValue(SwitchBearingBlock.FACING)));
        if (attachedState.canBeReplaced()) {
            return false;
        }
        TooltipHelper.addHint(tooltip, "hint.empty_bearing");
        return true;
    }

    @Override
    public void setAngle(final float forcedAngle) {
        angle = forcedAngle;
    }

    @Override
    public boolean onContraptionInteraction(final Player player, final InteractionHand hand) {
        if (level == null) {
            SwitchBearingDebug.warn(
                    "Interaction rejected: missing level controller={} player={}",
                    worldPosition,
                    player.getGameProfile().getName()
            );
            return false;
        }

        if (level.isClientSide) {
            return true;
        }

        final BlockState currentState = getBlockState();
        if (!(currentState.getBlock() instanceof SwitchBearingBlock)
                || !currentState.hasProperty(SwitchBearingBlock.POWERED)) {
            SwitchBearingDebug.warn(
                    "Interaction rejected: invalid controller state controller={} state={}",
                    worldPosition,
                    currentState
            );
            return false;
        }

        final SwitchMode mode = switchMode == null ? SwitchMode.IMPULSE : switchMode.get();

        switch (mode) {
            case IMPULSE -> {
                analogChangeTimer = 0;
                setPower(15, false);
                pulseTicks = SwitchBearingBlock.PULSE_TICKS;
                setChanged();
                click(0.3F, 0.6F);
                level.gameEvent(player, GameEvent.BLOCK_ACTIVATE, worldPosition);
            }
            case TOGGLE -> {
                pulseTicks = 0;
                analogChangeTimer = 0;
                final boolean lit = power == 0;
                setPower(lit ? 15 : 0, false);
                click(0.3F, lit ? 0.6F : 0.5F);
                level.gameEvent(
                        player,
                        lit ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE,
                        worldPosition
                );
            }
            case ANALOG -> {
                pulseTicks = 0;
                changeAnalogState(player.isShiftKeyDown());
            }
        }

        SwitchBearingDebug.info(
                "Switch interaction controller={} player={} hand={} mode={} power={} running={}",
                worldPosition,
                player.getGameProfile().getName(),
                hand,
                mode,
                power,
                running
        );

        return true;
    }

    public ControlledContraptionEntity getMovedContraption() {
        return movedContraption;
    }
}
