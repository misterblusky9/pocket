package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.create.SwitchPistonContraption;
import com.misterblusky9.pocket.debug.SwitchPistonDebug;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ContraptionCollider;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption.MovementMode;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock.PistonState;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlockEntity;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;

public class SwitchPistonBlockEntity extends MechanicalPistonBlockEntity
        implements SwitchControllerBlockEntity {

    protected SwitchModeBehaviour switchMode;
    private static final int ANALOG_SETTLE_TICKS = 15;

    private int pulseTicks;
    private int power;
    private int analogChangeTimer;

    public SwitchPistonBlockEntity(final BlockPos pos, final BlockState state) {
        super(ModBlockEntities.SWITCH_PISTON.get(), pos, state);
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.remove(movementMode);
        switchMode = new SwitchModeBehaviour(
                Component.translatable("pocket.switch.control_mode"), this, getMovementModeSlot());
        switchMode.withCallback(value -> onSwitchModeChanged());
        behaviours.add(switchMode);
    }

    @Override
    protected MovementMode getMovementMode() {
        return MovementMode.MOVE_NEVER_PLACE;
    }

    @Override
    protected void write(
            final CompoundTag tag,
            final HolderLookup.Provider registries,
            final boolean clientPacket
    ) {
        if (pulseTicks > 0) {
            tag.putInt("PulseTicks", pulseTicks);
        }
        tag.putInt("Power", power);
        if (analogChangeTimer > 0) {
            tag.putInt("AnalogChangeTimer", analogChangeTimer);
        }
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(
            final CompoundTag compound,
            final HolderLookup.Provider registries,
            final boolean clientPacket
    ) {
        pulseTicks = compound.getInt("PulseTicks");
        power = compound.getInt("Power");
        analogChangeTimer = compound.getInt("AnalogChangeTimer");
        super.read(compound, registries, clientPacket);
    }

    @Override
    public void tick() {
        tickPulse();
        tickAnalogRelease();
        tickUnpoweredAssembly();
        super.tick();
    }

    private void tickUnpoweredAssembly() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (!assembleNextTick || running || getSpeed() != 0) {
            return;
        }

        assembleNextTick = false;
        waitingForSpeedChange = false;
        try {
            assemble();
            lastException = null;
        } catch (final AssemblyException e) {
            lastException = e;
        }
        sendData();
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
        if (!(state.getBlock() instanceof SwitchPistonBlock) || !state.hasProperty(SwitchPistonBlock.POWERED)) {
            return;
        }

        final boolean lit = power > 0;
        BlockState updated = state;
        if (state.getValue(SwitchPistonBlock.POWERED) != lit) {
            updated = state.setValue(SwitchPistonBlock.POWERED, lit);
            level.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS);
        }

        if (!deferNeighbours) {
            SwitchPistonBlock.updateRedstoneNeighbours(updated, level, worldPosition);
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
        if (state.getBlock() instanceof SwitchPistonBlock) {
            SwitchPistonBlock.updateRedstoneNeighbours(state, level, worldPosition);
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

    public void toggleAssembly() {
        waitingForSpeedChange = false;
        if (running) {
            disassemble();
            return;
        }
        assembleNextTick = true;
    }

    @Override
    public void assemble() throws AssemblyException {
        if (!(level.getBlockState(worldPosition).getBlock() instanceof SwitchPistonBlock)) {
            return;
        }

        final Direction direction = getBlockState().getValue(BlockStateProperties.FACING);
        SwitchPistonDebug.info(
                "Assembly requested controller={} facing={} speed={}",
                worldPosition, direction, getSpeed()
        );

        final SwitchPistonContraption contraption =
                new SwitchPistonContraption(direction, getMovementSpeed() < 0);
        if (!contraption.assemble(level, worldPosition)) {
            return;
        }

        final Direction positive = Direction.get(AxisDirection.POSITIVE, direction.getAxis());
        final Direction movementDirection =
                getSpeed() > 0 ^ direction.getAxis() != Axis.Z ? positive : positive.getOpposite();

        final boolean powered = getSpeed() != 0;

        final BlockPos anchor =
                contraption.anchor.relative(direction, contraption.getInitialExtensionProgress());
        if (powered && ContraptionCollider.isCollidingWithWorld(level, contraption,
                anchor.relative(movementDirection), movementDirection)) {
            return;
        }

        extensionLength = contraption.getExtensionLength();

        if (powered) {
            final float resultingOffset =
                    contraption.getInitialExtensionProgress() + Math.signum(getMovementSpeed()) * .5f;
            if (resultingOffset <= 0 || resultingOffset >= extensionLength) {
                return;
            }
        }

        running = true;
        offset = contraption.getInitialExtensionProgress();
        sendData();
        clientOffsetDiff = 0;

        final BlockPos startPos =
                BlockPos.ZERO.relative(direction, contraption.getInitialExtensionProgress());
        contraption.removeBlocksFromWorld(level, startPos);
        movedContraption = ControlledContraptionEntity.create(getLevel(), this, contraption);
        resetContraptionToOffset();
        forceMove = true;
        level.addFreshEntity(movedContraption);

        SwitchPistonDebug.info(
                "Assembly complete controller={} entityId={} blocks={} interactionBounds={}",
                worldPosition,
                movedContraption.getId(),
                contraption.getBlocks().size(),
                contraption.getInteractionBounds()
        );

        AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, worldPosition);

        if (contraption.containsBlockBreakers()) {
            award(AllAdvancements.CONTRAPTION_ACTORS);
        }
    }

    @Override
    public void disassemble() {
        if (!running && movedContraption == null) {
            return;
        }
        if (!remove) {
            getLevel().setBlock(
                    worldPosition,
                    getBlockState().setValue(MechanicalPistonBlock.STATE, PistonState.EXTENDED),
                    3 | 16
            );
        }
        if (movedContraption != null) {
            resetContraptionToOffset();
            movedContraption.disassemble();
            AllSoundEvents.CONTRAPTION_DISASSEMBLE.playOnServer(level, worldPosition);
        }
        running = false;
        movedContraption = null;
        sendData();

        if (remove) {
            ModBlocks.SWITCH_PISTON.get()
                    .playerWillDestroy(level, worldPosition, getBlockState(), null);
        }
    }

    @Override
    public boolean onContraptionInteraction(final Player player, final InteractionHand hand) {
        if (level == null) {
            SwitchPistonDebug.warn(
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
        if (!(currentState.getBlock() instanceof SwitchPistonBlock)
                || !currentState.hasProperty(SwitchPistonBlock.POWERED)) {
            SwitchPistonDebug.warn(
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
                pulseTicks = SwitchPistonBlock.PULSE_TICKS;
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

        SwitchPistonDebug.info(
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
}
