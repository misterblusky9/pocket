package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pym.api.InternalForces;
import com.misterblusky9.pym.api.Pym;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.util.SimLevelUtil;
import java.util.List;
import java.util.UUID;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
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
import org.joml.Vector3dc;

public class FlexibleCouplingBlockEntity extends KineticBlockEntity implements BlockEntitySubLevelActor {
    private static final Vector3d frictionForce = new Vector3d();
    private static final Vector3d frictionTorque = new Vector3d();
    private static final Vector3d localLinearVelocity = new Vector3d();
    private static final Vector3d localAngularVelocity = new Vector3d();
    private static final Vector3d expectedVelocity = new Vector3d();
    private static final Vector3d localDampingPointForce = new Vector3d();
    private static final double RIGIDITY = 4.0;
    private static final double TIME_TO_SNAP = 0.25;
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
    private ForceTotal forceTotal;
    private ForceTotal partnerForceTotal;
    private double snappingTime;

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
        double linkScale = Pym.scale().linkFactor(
                Sable.HELPER.getContaining(level, worldPosition), Sable.HELPER.getContaining(level, partnerPos));
        double currentLength = Sable.HELPER.distanceSquaredWithSubLevels(
                level, worldPosition.getCenter(), partnerPos.getCenter()) / (linkScale * linkScale) + 1.0;
        if (delta < 0.0 && currentLength > newDesiredLength * newDesiredLength * 4.0) {
            return "too_stretched";
        }
        if (delta > 0.0 && currentLength < newDesiredLength * newDesiredLength / 4.0) {
            return "too_compressed";
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
        if (snappingTime > TIME_TO_SNAP) {
            FlexibleCouplingBlockEntity partner = getPartner();
            dropShaft();
            unlink();
            if (partner != null) {
                partner.unlink();
            }
            return;
        }
        if (FlexibleLinkScale.crossesBoundary(this)) {
            FlexibleCouplingBlockEntity partner = getPartner();
            dropShaft();
            unlink();
            if (partner != null) {
                partner.unlink();
            }
            return;
        }
        if (SimLevelUtil.isAreaActuallyLoaded(getLevel(), partnerPos, 1)) {
            if (getPartner() == null) {
                if (++ticksWithoutPartner > 20) {
                    unlink();
                }
            } else {
                ticksWithoutPartner = 0;
            }
        }
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        FlexibleCouplingBlockEntity partner = getPartner();
        if (partnerPos == null
                || !SimLevelUtil.isAreaActuallyLoaded(getLevel(), partnerPos, 1)
                || partner == null
                || ticksWithoutPartner != 0.0F) {
            return;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
        SubLevelPhysicsSystem system = container.physicsSystem();
        system.updatePose(subLevel);
        ServerSubLevel partnerSubLevel = this.partnerSubLevel != null
                ? (ServerSubLevel) container.getSubLevel(this.partnerSubLevel) : null;
        if (this.partnerSubLevel != null && partnerSubLevel == null) {
            return;
        }
        if (partnerSubLevel != null && !isController) {
            return;
        }
        if (partnerSubLevel == subLevel) {
            return;
        }
        if (partnerSubLevel != null) {
            system.updatePose(partnerSubLevel);
        }

        BlockState state = getBlockState();
        Vector3dc center = getCenter();
        Vector3dc partnerCenter = partner.getCenter();
        Vector3d velo1 = Sable.HELPER.getVelocity(level, center, new Vector3d());
        Vector3d velo2 = Sable.HELPER.getVelocity(level, partnerCenter, new Vector3d());
        Vector3d positionA = subLevel.logicalPose().transformPosition(center, new Vector3d());
        Vector3d positionB = partnerSubLevel != null
                ? partnerSubLevel.logicalPose().transformPosition(partner.getCenter())
                : partner.getCenter();
        Vector3d relativeVelo = velo1.sub(velo2);
        Vector3d dampingPointForce = new Vector3d(relativeVelo);
        dampingPointForce.mul(-4.5);
        double linkScale = Pym.scale().linkFactor(subLevel, partnerSubLevel);
        double desiredLength = ((isController ? this.desiredLength : partner.desiredLength) - 0.75) * linkScale;
        if (positionA.distanceSquared(positionB) > Mth.square(getSnappingDistance() * linkScale)) {
            snappingTime += timeStep;
        } else {
            snappingTime = 0.0;
        }

        Vector3d globalNormalA = JOMLConversion.atLowerCornerOf(state.getValue(FlexibleCouplingBlock.FACING).getNormal());
        Vector3d globalNormalB = JOMLConversion.atLowerCornerOf(
                partner.getBlockState().getValue(FlexibleCouplingBlock.FACING).getNormal());
        subLevel.logicalPose().transformNormal(globalNormalA);
        if (partnerSubLevel != null) {
            partnerSubLevel.logicalPose().transformNormal(globalNormalB);
        }

        Vector3d torque = globalNormalA.cross(globalNormalB.negate(), new Vector3d()).mul(20.0).mul(timeStep);
        Vector3d mediumNormal = globalNormalA.normalize(new Vector3d())
                .lerp(globalNormalB.normalize(new Vector3d()), 0.5);
        Vector3dc middle = new Vector3d(positionA.x, positionA.y, positionA.z).lerp(positionB, 0.5);
        Vector3d desireA = middle.fma(-desiredLength / 2.0, mediumNormal, new Vector3d());
        Vector3d alignmentForce = desireA.sub(positionA.x, positionA.y, positionA.z);
        Vector3d hookesPointForce = alignmentForce.mul(145.0);
        Vector3d angVelo1 = new Vector3d();
        Vector3d angVelo2 = new Vector3d();
        handle.getAngularVelocity(angVelo1);
        if (partnerSubLevel != null) {
            RigidBodyHandle.of(partnerSubLevel).getAngularVelocity(angVelo2);
        }

        Vector3d relativeAngVelo = angVelo1.sub(angVelo2);
        Vector3d dampingTorque = new Vector3d();
        if (mediumNormal.lengthSquared() > 0.0) {
            mediumNormal.normalize();
            double dot = mediumNormal.dot(relativeAngVelo);
            relativeAngVelo.set(mediumNormal).mul(dot);
            dampingTorque.fma(-2.0, relativeAngVelo);
        }
        hookesPointForce.mul(RIGIDITY);
        torque.mul(RIGIDITY);
        dampingTorque.mul(RIGIDITY);
        dampingPointForce.mul(RIGIDITY);
        if (forceTotal == null || partnerForceTotal == null) {
            forceTotal = new ForceTotal();
            partnerForceTotal = new ForceTotal();
        }

        applyLocalDamping(subLevel, handle, forceTotal, center, dampingPointForce, dampingTorque, timeStep);
        forceTotal.applyImpulseAtPoint(subLevel, center,
                subLevel.logicalPose().transformNormalInverse(new Vector3d(hookesPointForce)).mul(timeStep));
        forceTotal.applyLinearAndAngularImpulse(JOMLConversion.ZERO,
                subLevel.logicalPose().transformNormalInverse(torque, new Vector3d()));
        try (InternalForces ignored = Pym.internalForces(subLevel)) {
            handle.applyForcesAndReset(forceTotal);
        }
        if (partnerSubLevel != null) {
            RigidBodyHandle partnerHandle = RigidBodyHandle.of(partnerSubLevel);
            applyLocalDamping(partnerSubLevel, partnerHandle, partnerForceTotal, partnerCenter,
                    dampingPointForce.negate(), dampingTorque.negate(), timeStep);
            partnerForceTotal.applyImpulseAtPoint(partnerSubLevel, partnerCenter,
                    partnerSubLevel.logicalPose().transformNormalInverse(hookesPointForce).mul(-timeStep));
            partnerForceTotal.applyLinearAndAngularImpulse(JOMLConversion.ZERO,
                    partnerSubLevel.logicalPose().transformNormalInverse(torque.negate()));
            try (InternalForces ignored = Pym.internalForces(partnerSubLevel)) {
                partnerHandle.applyForcesAndReset(partnerForceTotal);
            }
        }
    }

    private void applyLocalDamping(ServerSubLevel subLevel, RigidBodyHandle handle,
                                   ForceTotal forceTotal, Vector3dc worldSpringPos,
                                   Vector3dc dampingPointForce, Vector3dc dampingTorque,
                                   double timeStep) {
        Pose3d pose = subLevel.logicalPose();
        handle.getAngularVelocity(localAngularVelocity);
        handle.getLinearVelocity(localLinearVelocity);
        pose.orientation().transformInverse(localAngularVelocity);
        pose.orientation().transformInverse(localLinearVelocity);
        Vector3dc centerOfMass = subLevel.getMassTracker().getCenterOfMass();
        pose.orientation().transformInverse(dampingPointForce, localDampingPointForce);
        Vector3d angularDamping = new Vector3d();
        angularDamping.add(dampingTorque);
        pose.orientation().transformInverse(angularDamping);
        angularDamping.add(worldSpringPos.sub(centerOfMass, new Vector3d()).cross(localDampingPointForce));
        Vector3d linearDamping = new Vector3d();
        linearDamping.add(localDampingPointForce);
        frictionForce.set(linearDamping);
        frictionTorque.set(angularDamping);
        expectedVelocity.set(frictionForce);
        expectedVelocity.mul(subLevel.getMassTracker().getInverseMass());
        expectedVelocity.mul(timeStep);
        double forceScale = getClampingFactor(localLinearVelocity, expectedVelocity);
        expectedVelocity.set(frictionTorque);
        subLevel.getMassTracker().getInverseInertiaTensor().transform(expectedVelocity);
        expectedVelocity.mul(timeStep);
        double torqueScale = getClampingFactor(localAngularVelocity, expectedVelocity);
        frictionForce.mul(forceScale * timeStep);
        frictionTorque.mul(torqueScale * timeStep);
        forceTotal.applyLinearAndAngularImpulse(frictionForce, frictionTorque);
    }

    private double getClampingFactor(Vector3dc currentVelocity, Vector3dc expectedVelocityChange) {
        double k = -currentVelocity.dot(expectedVelocityChange);
        double v = currentVelocity.lengthSquared();
        if (k < 0.0) {
            return 0.0;
        }
        if (10.0 * k < v) {
            return 1.0;
        }
        return v < 1.0E-10 ? v / (k + 1.0E-10) : v * (1.0 - Math.exp(-k / v)) / k;
    }

    @Nullable
    @Override
    public Iterable<SubLevel> sable$getConnectionDependencies() {
        if (partnerSubLevel != null) {
            SubLevel subLevel = SubLevelContainer.getContainer(level).getSubLevel(partnerSubLevel);
            if (subLevel != null) {
                return List.of(subLevel);
            }
        }
        return List.of();
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
        snappingTime = 0.0;
        ticksWithoutPartner = 0.0F;
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
        snappingTime = 0.0;
        ticksWithoutPartner = 0.0F;
        setChanged();
        notifyUpdate();
        attachKinetics();
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

    public double getSnappingDistance() {
        return desiredLength * SNAP_STRETCH + SNAP_SLACK;
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
