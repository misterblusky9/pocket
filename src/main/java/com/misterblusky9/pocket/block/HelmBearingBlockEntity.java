package com.misterblusky9.pocket.block;

import com.google.common.collect.ImmutableList;
import com.misterblusky9.pocket.create.HelmBearingContraption;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.IBearingBlockEntity;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.foundation.utility.ServerSpeedProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public class HelmBearingBlockEntity extends GeneratingKineticBlockEntity
        implements IBearingBlockEntity, IDisplayAssemblyExceptions {
    public static final int RPM = 16;

    public boolean held;
    public ScrollValueBehaviour angleInput;
    public float targetAngle;
    public float targetAngleToUpdate;

    protected ControlledContraptionEntity movedContraption;
    protected boolean running;
    public boolean assembleNextTick;
    protected AssemblyException lastException;

    private int inUse;
    private float angle;
    private float prevAngle;
    private float generatedSpeed;
    private float logicalSpeed;
    private float clientAngleDiff;
    private double sequencedAngleLimit = -1;

    public HelmBearingBlockEntity(final BlockPos pos, final BlockState state) {
        super(ModBlockEntities.HELM_BEARING.get(), pos, state);
        setLazyTickRate(3);
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
        angleInput = new HelmBearingScrollValueBehaviour(this).between(1, 360);
        angleInput.value = 180;
        behaviours.add(angleInput);
        registerAwardables(behaviours, AllAdvancements.CONTRAPTION_ACTORS);
    }

    public void startHolding() {
        held = true;
        notifyUpdate();
    }

    public void stopHolding() {
        held = false;
        notifyUpdate();
    }

    public float directionConvert(final float value) {
        return -KineticBlockEntity.convertToDirection(value, getBlockState().getValue(HelmBearingBlock.FACING));
    }

    public void setTargetAngleToUpdate(final float target) {
        if (!Float.isFinite(target) || angleInput == null) {
            return;
        }
        targetAngleToUpdate = Mth.clamp(target, -angleInput.getValue(), angleInput.getValue());
    }

    public void updateTargetAngle(float absoluteTarget) {
        absoluteTarget = Mth.clamp(absoluteTarget, -angleInput.getValue(), angleInput.getValue());
        if (targetAngle == absoluteTarget) {
            return;
        }

        targetAngle = absoluteTarget;
        final float relativeAngle = absoluteTarget - angle;

        if (Math.abs(relativeAngle) < 0.001F && inUse <= 0) {
            stopGeneratedMotion();
            return;
        }

        final float rotationSpeed = RPM * Math.signum(relativeAngle);
        if (rotationSpeed == 0) {
            return;
        }

        final float relativeValue = relativeAngle / rotationSpeed;
        if (relativeValue <= 0 && inUse <= 0) {
            stopGeneratedMotion();
            return;
        }

        final double degreesPerTick = KineticBlockEntity.convertToAngular(rotationSpeed);
        inUse = (int) Math.ceil(relativeAngle / degreesPerTick) + 2;
        sequenceContext = new SequencedGearshiftBlockEntity.SequenceContext(
                SequencerInstructions.TURN_ANGLE, relativeValue);
        sequencedAngleLimit = Math.abs(relativeAngle);
        logicalSpeed = rotationSpeed;
        generatedSpeed = KineticBlockEntity.convertToDirection(
                logicalSpeed, getBlockState().getValue(HelmBearingBlock.FACING));
        updateGeneratedRotation();
        sendData();
    }

    private void stopGeneratedMotion() {
        inUse = 0;
        sequenceContext = null;
        sequencedAngleLimit = -1;
        generatedSpeed = 0;
        logicalSpeed = 0;
        updateGeneratedRotation();
        sendData();
    }

    @Override
    protected void copySequenceContextFrom(final KineticBlockEntity sourceBE) {
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
        compound.putBoolean("Held", held);
        compound.putFloat("Angle", angle);
        compound.putFloat("TargetAngle", targetAngle);
        compound.putFloat("TargetAngleToUpdate", targetAngleToUpdate);
        compound.putInt("InUse", inUse);
        compound.putFloat("GeneratedSpeed", generatedSpeed);
        compound.putFloat("LogicalSpeed", logicalSpeed);
        if (sequencedAngleLimit >= 0) {
            compound.putDouble("SequencedAngleLimit", sequencedAngleLimit);
        }
        AssemblyException.write(compound, registries, lastException);
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket) {
        if (wasMoved) {
            super.read(compound, registries, clientPacket);
            return;
        }

        final float previousAngle = angle;
        running = compound.getBoolean("Running");
        held = compound.getBoolean("Held");
        angle = compound.getFloat("Angle");
        targetAngle = compound.getFloat("TargetAngle");
        targetAngleToUpdate = compound.contains("TargetAngleToUpdate")
                ? compound.getFloat("TargetAngleToUpdate") : targetAngle;
        inUse = compound.getInt("InUse");
        generatedSpeed = compound.getFloat("GeneratedSpeed");
        logicalSpeed = compound.getFloat("LogicalSpeed");
        sequencedAngleLimit = compound.contains("SequencedAngleLimit")
                ? compound.getDouble("SequencedAngleLimit") : -1;
        lastException = AssemblyException.read(compound, registries);
        super.read(compound, registries, clientPacket);

        if (clientPacket && running && (movedContraption == null || !movedContraption.isStalled())) {
            clientAngleDiff = angle - previousAngle;
            angle = previousAngle;
        }
        if (!running && clientPacket) {
            movedContraption = null;
        }
    }

    @Override
    public float getInterpolatedAngle(float partialTicks) {
        if (isVirtual()) {
            return Mth.lerp(partialTicks + 0.5F, prevAngle, angle);
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

    public float getInteractionAngle(final float partialTicks) {
        return getInterpolatedAngle(partialTicks);
    }

    public float getAngle() {
        return angle;
    }

    public float getAngularSpeed() {
        float speed = convertToAngular(getLogicalSpeed());
        if (getSpeed() == 0 || getLogicalSpeed() == 0) {
            speed = 0;
        }
        if (level.isClientSide) {
            speed *= ServerSpeedProvider.get();
            speed += clientAngleDiff / 3.0F;
        }
        return speed;
    }

    public float getLogicalSpeed() {
        return inUse == 0 ? 0 : logicalSpeed;
    }

    @Override
    public float getGeneratedSpeed() {
        return inUse == 0 ? 0 : generatedSpeed;
    }

    @Override
    protected Block getStressConfigKey() {
        return ModBlocks.HELM_BEARING.get();
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
        if (!(level.getBlockState(worldPosition).getBlock() instanceof HelmBearingBlock)) {
            return;
        }

        final Direction direction = getBlockState().getValue(HelmBearingBlock.FACING);
        final HelmBearingContraption contraption = new HelmBearingContraption(direction);
        try {
            if (!contraption.assemble(level, worldPosition)) {
                return;
            }
            lastException = null;
        } catch (final AssemblyException e) {
            lastException = e;
            sendData();
            return;
        }

        contraption.removeBlocksFromWorld(level, BlockPos.ZERO);
        movedContraption = ControlledContraptionEntity.create(level, this, contraption);
        final BlockPos anchor = worldPosition.relative(direction);
        movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        movedContraption.setRotationAxis(direction.getAxis());
        level.addFreshEntity(movedContraption);

        AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, worldPosition);
        if (contraption.containsBlockBreakers()) {
            award(AllAdvancements.CONTRAPTION_ACTORS);
        }

        running = true;
        angle = 0;
        prevAngle = 0;
        targetAngle = 0;
        targetAngleToUpdate = 0;
        sendData();
        applyRotation();
    }

    public void disassemble() {
        if (!running && movedContraption == null) {
            return;
        }

        held = false;
        stopGeneratedMotion();
        angle = 0;
        prevAngle = 0;
        targetAngle = 0;
        targetAngleToUpdate = 0;

        if (movedContraption != null) {
            movedContraption.disassemble();
            AllSoundEvents.CONTRAPTION_DISASSEMBLE.playOnServer(level, worldPosition);
        }

        movedContraption = null;
        running = false;
        assembleNextTick = false;
        sendData();
    }

    @Override
    public void tick() {
        super.tick();
        prevAngle = angle;

        if (level.isClientSide) {
            clientAngleDiff /= 2.0F;
        }

        if (!level.isClientSide && assembleNextTick) {
            assembleNextTick = false;
            if (!running) {
                assemble();
            }
        }

        if (getGeneratedSpeed() != 0) {
            integrateAngle();
        }

        if (inUse > 0) {
            inUse--;
            if (inUse == 0 && !level.isClientSide) {
                angle = targetAngle;
                stopGeneratedMotion();
            }
        } else if (!level.isClientSide && angleInput != null) {
            updateTargetAngle(targetAngleToUpdate);
        }

        if (running) {
            applyRotation();
        }
    }

    private void integrateAngle() {
        float angularSpeed = getAngularSpeed();
        if (sequencedAngleLimit >= 0) {
            angularSpeed = (float) Mth.clamp(angularSpeed, -sequencedAngleLimit, sequencedAngleLimit);
            sequencedAngleLimit = Math.max(0, sequencedAngleLimit - Math.abs(angularSpeed));
        }
        angle += angularSpeed;
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
        final BlockState state = getBlockState();
        if (state.hasProperty(BlockStateProperties.FACING)) {
            movedContraption.setRotationAxis(state.getValue(BlockStateProperties.FACING).getAxis());
        }
    }

    @Override
    public void attach(final ControlledContraptionEntity contraption) {
        final BlockState state = getBlockState();
        if (!(contraption.getContraption() instanceof BearingContraption)) {
            return;
        }
        if (!state.hasProperty(HelmBearingBlock.FACING)) {
            return;
        }

        movedContraption = contraption;
        setChanged();
        final BlockPos anchor = worldPosition.relative(state.getValue(HelmBearingBlock.FACING));
        movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        if (!level.isClientSide) {
            running = true;
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
    public void setAngle(final float forcedAngle) {
        angle = forcedAngle;
    }


    public ControlledContraptionEntity getMovedContraption() {
        return movedContraption;
    }

    private static final class HelmBearingScrollValueBehaviour extends ScrollValueBehaviour {
        private HelmBearingScrollValueBehaviour(final HelmBearingBlockEntity be) {
            super(Component.translatable("pocket.helm_bearing.angle_limit"), be, be.getMovementModeSlot());
            withFormatter(v -> Math.abs(v) + CreateLang.translateDirect("generic.unit.degrees").getString());
        }

        @Override
        public ValueSettingsBoard createBoard(final Player player, final BlockHitResult hitResult) {
            return new ValueSettingsBoard(
                    label,
                    360,
                    45,
                    ImmutableList.of(Component.literal("\u27f3").withStyle(ChatFormatting.BOLD)),
                    new ValueSettingsFormatter(this::formatValue)
            );
        }

        private MutableComponent formatValue(final ValueSettings settings) {
            return Component.literal(String.valueOf(Math.max(1, Math.abs(settings.value()))))
                    .append(CreateLang.translateDirect("generic.unit.degrees"));
        }
    }
}
