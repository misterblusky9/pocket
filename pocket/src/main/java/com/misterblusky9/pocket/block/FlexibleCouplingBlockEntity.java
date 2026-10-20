package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.item.ModItems;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.util.SimLevelUtil;
import java.util.List;
import java.util.UUID;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public class FlexibleCouplingBlockEntity extends KineticBlockEntity {
    // Disconnect after a quarter-second continuously beyond the configured reach.
    private static final int OVERSTRETCH_TICKS_TO_SNAP = 5;
    private static final double SNAP_STRETCH = 1.5;
    private static final double SNAP_SLACK = 0.5;
    public static final double MIN_LENGTH = 1.0;
    public static final double MAX_LENGTH = 9.0;

    protected LerpedFloat renderLength = LerpedFloat.linear();
    protected double desiredLength;
    protected boolean isController;
    protected boolean assembling;
    protected BlockPos partnerPos;
    @Nullable
    private UUID partnerSubLevel;
    private float ticksWithoutPartner = 0.0F;
    private int overstretchTicks;

    public FlexibleCouplingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        renderLength.chase(0.0, 0.2, LerpedFloat.Chaser.EXP);
    }

    public FlexibleCouplingBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.FLEXIBLE_COUPLING.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public Vector3d getCenter() {
        Direction facing = getBlockState().getValue(FlexibleCouplingBlock.FACING);
        Vec3i facingVec = facing.getNormal();
        double scale = 0.25;
        return JOMLConversion.atCenterOf(worldPosition)
                .sub(facingVec.getX() * scale, facingVec.getY() * scale, facingVec.getZ() * scale);
    }

    @Nullable
    public String tryChangeLengthOrError(Level level, double delta) {
        if (delta > 0.0 && desiredLength >= MAX_LENGTH) {
            return "max_length";
        }
        if (delta < 0.0 && desiredLength <= MIN_LENGTH) {
            return "min_length";
        }
        double newDesiredLength = Math.clamp(desiredLength + delta, MIN_LENGTH, MAX_LENGTH);
        newDesiredLength = Math.round(newDesiredLength / 0.25) * 0.25;
        // Prevent tightening an already stretched shaft past its snap threshold.
        // Distance and desiredLength are both in world blocks (including across sublevel scales).
        if (delta < 0.0 && partnerPos != null &&
                Sable.HELPER.distanceSquaredWithSubLevels(level, worldPosition.getCenter(),
                        partnerPos.getCenter()) > Math.pow(snappingDistance(newDesiredLength), 2.0)) {
            return "too_stretched";
        }
        desiredLength = newDesiredLength;
        setChanged();
        sendData();
        if (level.getBlockEntity(partnerPos) instanceof FlexibleCouplingBlockEntity partnerBE) {
            partnerBE.desiredLength = desiredLength;
            partnerBE.setChanged();
            partnerBE.sendData();
        }
        return null;
    }

    public double getRenderLength(float pt) {
        return renderLength.getValue(pt);
    }

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide) {
            renderLength.updateChaseTarget((float) desiredLength);
            renderLength.tickChaser();
            return;
        }
        if (partnerPos == null) {
            return;
        }
        if (!SimLevelUtil.isAreaActuallyLoaded(getLevel(), partnerPos, 1)) {
            // Unloaded endpoints cannot be measured reliably. Do not count them as stretched.
            overstretchTicks = 0;
            return;
        }
        FlexibleCouplingBlockEntity partner = getPartner();
        if (partner == null) {
            overstretchTicks = 0;
            if (++ticksWithoutPartner > 20) {
                unlink();
            }
            return;
        }
        ticksWithoutPartner = 0;
        if (!isController) {
            return; // Only one side measures, breaks, and drops the shaft.
        }
        double distanceSquared = Sable.HELPER.distanceSquaredWithSubLevels(
                level, worldPosition.getCenter(), partnerPos.getCenter());
        double maxDistance = getSnappingDistance();
        if (Double.isFinite(distanceSquared) && distanceSquared > maxDistance * maxDistance) {
            if (++overstretchTicks >= OVERSTRETCH_TICKS_TO_SNAP) {
                dropShaft();
                unlink();
                partner.unlink();
            }
        } else {
            overstretchTicks = 0;
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        if (level.isClientSide || partnerPos == null || assembling) {
            return;
        }
        FlexibleCouplingBlockEntity partner = getPartner();
        dropShaft();
        partnerPos = null;
        if (partner != null) {
            partner.unlink();
        }
    }

    @Override
    public List<BlockPos> addPropagationLocations(IRotate block, BlockState state,
                                                  List<BlockPos> neighbours) {
        if (partnerPos != null) {
            neighbours.add(partnerPos);
        }
        return super.addPropagationLocations(block, state, neighbours);
    }

    @Override
    public boolean isCustomConnection(KineticBlockEntity other, BlockState state,
                                      BlockState otherState) {
        return isPartner(other);
    }

    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom,
                                     BlockState stateTo, BlockPos diff,
                                     boolean connectedViaAxes, boolean connectedViaCogs) {
        if (!isPartner(target)) {
            return 0;
        }
        int from = stateFrom.getValue(FlexibleCouplingBlock.FACING).getAxisDirection().getStep();
        int to = stateTo.getValue(FlexibleCouplingBlock.FACING).getAxisDirection().getStep();
        return -from * to;
    }

    private boolean isPartner(KineticBlockEntity other) {
        return other instanceof FlexibleCouplingBlockEntity && other.getBlockPos().equals(partnerPos);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Controller", isController);
        tag.putDouble("DesiredLength", desiredLength);
        if (partnerPos == null) {
            return;
        }
        SubLevelSchematicSerializationContext schematicContext =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (schematicContext != null
                && schematicContext.getType() != SubLevelSchematicSerializationContext.Type.PLACE) {
            BlockPos goal = partnerPos;
            UUID id = partnerSubLevel;
            if (id != null) {
                SubLevelSchematicSerializationContext.SchematicMapping mapping = schematicContext.getMapping(id);
                if (mapping != null) {
                    id = mapping.newUUID();
                    goal = mapping.transform().apply(goal);
                } else {
                    id = null;
                    goal = null;
                }
            } else if (schematicContext.getBoundingBox().contains(goal.getX(), goal.getY(), goal.getZ())) {
                goal = schematicContext.getPlaceTransform().apply(goal);
            } else {
                goal = null;
            }
            if (goal != null) {
                tag.putLong("Goal", goal.asLong());
            }
            if (id != null) {
                tag.putUUID("GoalSubLevel", id);
            }
        } else {
            if (partnerSubLevel != null) {
                tag.putUUID("GoalSubLevel", partnerSubLevel);
            }
            BlockPos goal = partnerPos;
            if (schematicContext != null && partnerSubLevel == null) {
                goal = schematicContext.getSetupTransform().apply(goal);
            }
            tag.putLong("Goal", goal.asLong());
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        isController = tag.getBoolean("Controller");
        partnerPos = null;
        partnerSubLevel = null;
        desiredLength = tag.getDouble("DesiredLength");
        if (renderLength.getValue() == 0.0F) {
            renderLength.setValue(desiredLength);
        }

        SubLevelSchematicSerializationContext schematicContext =
                SubLevelSchematicSerializationContext.getCurrentContext();
        boolean placingFromSchematic = schematicContext != null
                && schematicContext.getType() == SubLevelSchematicSerializationContext.Type.PLACE;
        SubLevelSchematicSerializationContext.SchematicMapping mapping = null;
        if (tag.hasUUID("GoalSubLevel")) {
            UUID subLevelID = tag.getUUID("GoalSubLevel");
            if (placingFromSchematic) {
                mapping = schematicContext.getMapping(subLevelID);
                if (mapping == null) {
                    partnerSubLevel = null;
                    partnerPos = null;
                    return;
                }
                subLevelID = mapping.newUUID();
            }
            partnerSubLevel = subLevelID;
        }

        if (tag.contains("Goal")) {
            BlockPos goal = BlockPos.of(tag.getLong("Goal"));
            if (placingFromSchematic) {
                goal = mapping != null
                        ? mapping.transform().apply(goal)
                        : schematicContext.getPlaceTransform().apply(goal);
            }
            partnerPos = goal;
        }
    }

    public boolean isLinked() {
        return partnerPos != null;
    }

    public void setPartnerPos(BlockPos pos, UUID subLevel) {
        partnerPos = pos;
        partnerSubLevel = subLevel;
        sendData();
    }

    public void link(BlockPos pos, @Nullable UUID subLevel, boolean controller, double length) {
        detachKinetics();
        partnerPos = pos;
        partnerSubLevel = subLevel;
        isController = controller;
        desiredLength = length;
        ticksWithoutPartner = 0.0F;
        overstretchTicks = 0;
        setChanged();
        notifyUpdate();
        attachKinetics();
    }

    public void unlink() {
        if (partnerPos == null) {
            return;
        }
        detachKinetics();
        partnerPos = null;
        partnerSubLevel = null;
        isController = false;
        overstretchTicks = 0;
        ticksWithoutPartner = 0.0F;
        setChanged();
        notifyUpdate();
        attachKinetics();
    }

    public double getSnappingDistance() {
        return snappingDistance(desiredLength);
    }

    private static double snappingDistance(double length) {
        return length * SNAP_STRETCH + SNAP_SLACK;
    }

    public void dropShaft() {
        if (level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            Block.popResource(level, worldPosition, new ItemStack(ModItems.FLEXIBLE_SHAFT.get()));
        }
    }

    public boolean isController() {
        return isController;
    }

    public void setController(boolean controller) {
        isController = controller;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        invalidateRenderBoundingBox();
    }

    @Nullable
    public FlexibleCouplingBlockEntity getPartner() {
        if (partnerPos == null) {
            return null;
        }
        return level.getBlockEntity(partnerPos) instanceof FlexibleCouplingBlockEntity partner ? partner : null;
    }

    @Override
    public AABB getRenderBoundingBox() {
        if (getPartner() == null) {
            return new AABB(getBlockPos());
        }
        Vec3 center = getBlockPos().getCenter();
        Vec3 partner = partnerPos.getCenter();
        SubLevel subLevel = Sable.HELPER.getContaining(this);
        SubLevel partnerSubLevel = Sable.HELPER.getContaining(level, partnerPos);
        if (partnerSubLevel != null) {
            partner = partnerSubLevel.logicalPose().transformPosition(partner);
        }
        if (subLevel != null) {
            partner = subLevel.logicalPose().transformPositionInverse(partner);
        }
        return new AABB(center, partner).inflate(3.0);
    }

    public void setDesiredLength(double desiredLength) {
        this.desiredLength = desiredLength;
    }

    public UUID getPartnerSubLevelID() {
        return partnerSubLevel;
    }
}
