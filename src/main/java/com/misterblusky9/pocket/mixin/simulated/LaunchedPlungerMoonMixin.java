package com.misterblusky9.pocket.mixin.simulated;

import com.misterblusky9.pocket.compat.aeronautics.AeronauticsPlungerAccess;
import com.misterblusky9.pocket.compat.simulated.MoonPlungerAttachment;
import com.misterblusky9.pocket.moon.MoonPhysicsState;
import com.misterblusky9.pocket.moon.MoonPhysicsTarget;
import com.misterblusky9.pocket.moon.MoonPlungerSpring;
import com.misterblusky9.pocket.moon.MoonSubLevel;
import com.misterblusky9.pocket.moon.MoonSubLevels;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.object.box.BoxPhysicsObject;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.entities.launched_plunger.LaunchedPlungerEntity", priority = 900, remap = false)
public abstract class LaunchedPlungerMoonMixin implements MoonPlungerAttachment {
    @Unique private static final BlockPos pocket$MOON_SENTINEL = new BlockPos(29_999_984, 2047, 29_999_984);
    @Unique private boolean pocket$moonAttached;
    @Unique private final Vector3d pocket$moonAnchor = new Vector3d();
    @Unique private final Vector3d pocket$moonNormal = new Vector3d(0.0D, 1.0D, 0.0D);
    @Unique private PhysicsConstraintHandle pocket$moonPairConstraint;
    @Unique private int pocket$constraintMoonRuntimeId = -1;
    @Unique private double pocket$constraintSubLevelScale = 1.0D;

    @Inject(method = "tick", at = @At("HEAD"), remap = false, require = 0)
    private void pocket$hitMoon(final CallbackInfo ci) {
        final Entity self = (Entity) (Object) this;
        if (!(self.level() instanceof final ServerLevel level)) return;

        if (this.pocket$moonAttached) {
            if (MoonPhysicsTarget.body(level) == null || MoonPhysicsTarget.state(level) == null) {
                this.pocket$detachMoon(self);
            }
            return;
        }

        if (AeronauticsPlungerAccess.isPlunged(self)) return;
        final MoonPhysicsState.State state = MoonPhysicsTarget.state(level);
        if (state == null) return;

        final Vec3 from = self.position();
        final Vec3 motion = self.getDeltaMovement();
        if (motion.lengthSqr() <= 1.0E-10D) return;
        final Vec3 to = from.add(motion);
        final MoonPhysicsState.RayHit moonHit = MoonPhysicsState.raycast(state, from, to);
        if (moonHit == null) return;

        final BlockHitResult blockHit = level.clip(new ClipContext(
                from,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                CollisionContext.empty()
        ));
        if (blockHit.getType() != HitResult.Type.MISS
                && from.distanceToSqr(blockHit.getLocation()) + 1.0E-7D
                < from.distanceToSqr(moonHit.worldPoint())) {
            return;
        }

        final Vector3d normalized = MoonPhysicsTarget.normalizedAnchor(state, moonHit.localPoint());
        final Vector3d localNormal = MoonPhysicsTarget.localNormal(state, moonHit.localPoint());
        if (normalized == null || localNormal == null) return;

        this.pocket$moonAttached = true;
        this.pocket$moonAnchor.set(normalized);
        this.pocket$moonNormal.set(localNormal);
        self.noPhysics = true;
        self.setDeltaMovement(Vec3.ZERO);
        AeronauticsPlungerAccess.setPlunged(self, true);
        AeronauticsPlungerAccess.setBlockPos(self, pocket$MOON_SENTINEL);
        this.pocket$updateMoonPosition(self, level);
    }

    @Inject(method = "tick", at = @At("TAIL"), remap = false, require = 0)
    private void pocket$followMoon(final CallbackInfo ci) {
        final Entity self = (Entity) (Object) this;
        if (!(self.level() instanceof final ServerLevel level) || !this.pocket$moonAttached) return;

        if (MoonPhysicsTarget.body(level) == null || MoonPhysicsTarget.state(level) == null) {
            this.pocket$detachMoon(self);
            return;
        }

        self.noPhysics = true;
        self.setDeltaMovement(Vec3.ZERO);
        AeronauticsPlungerAccess.setPlunged(self, true);
        AeronauticsPlungerAccess.setBlockPos(self, pocket$MOON_SENTINEL);
        this.pocket$updateMoonPosition(self, level);
        this.pocket$tickWorldPair(self, level);
    }

    @Inject(method = "physicsTick", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void pocket$physicsTickMoonPair(
            final ServerSubLevel subLevel,
            final RigidBodyHandle handle,
            final double timeStep,
            final CallbackInfo ci
    ) {
        final Entity self = (Entity) (Object) this;
        if (this.pocket$moonAttached) {
            this.pocket$removePairConstraint();
            ci.cancel();
            return;
        }

        final Entity other = AeronauticsPlungerAccess.getOther(self);
        if (!(other instanceof final MoonPlungerAttachment moonAttachment) || !moonAttachment.pocket$isMoonAttached()) {
            this.pocket$removePairConstraint();
            return;
        }

        if (!(self.level() instanceof final ServerLevel level)
                || !AeronauticsPlungerAccess.isPlunged(self)
                || !AeronauticsPlungerAccess.isPlunged(other)) {
            this.pocket$removePairConstraint();
            ci.cancel();
            return;
        }

        final BoxPhysicsObject moon = MoonPhysicsTarget.body(level);
        final Vector3d moonAnchor = moonAttachment.pocket$moonLocalAnchor(level);
        final Vec3 moonWorldAnchor = moonAttachment.pocket$moonWorldAnchor(level);
        if (moon == null || moonAnchor == null || moonWorldAnchor == null) {
            this.pocket$removePairConstraint();
            ci.cancel();
            return;
        }

        final MoonSubLevel shim = MoonSubLevels.get(level);
        if (shim == null) {
            this.pocket$removePairConstraint();
            ci.cancel();
            return;
        }
        shim.syncFromBody();
        final Vector3d moonPlotAnchor = new Vector3d(moonAnchor);

        final PhysicsPipeline pipeline = SubLevelPhysicsSystem.require(level).getPipeline();
        final Vec3 selfAttachment = AeronauticsPlungerAccess.getAttachmentPos(self);
        final Vector3d subLevelAnchor = JOMLConversion.toJOML(selfAttachment);
        final Vec3 subLevelWorldAnchor = subLevel.logicalPose().transformPosition(selfAttachment);

        final Vector3d pull = JOMLConversion.toJOML(moonWorldAnchor.subtract(subLevelWorldAnchor));
        final Vector3d localSubLevelPull = subLevel.logicalPose().transformNormalInverse(pull, new Vector3d());
        final Vector3d localMoonPull = shim.logicalPose().transformNormalInverse(pull, new Vector3d());

        final double inverseSubLevelMass = pocket$inverseNormalMass(
                subLevel.getMassTracker(), subLevelAnchor, localSubLevelPull);
        final double inverseMoonMass = pocket$inverseNormalMass(
                shim.getMassTracker(), moonPlotAnchor, localMoonPull);

        final MoonPlungerSpring.Impulse impulse = MoonPlungerSpring.impulse(
                pull.x, pull.y, pull.z, inverseSubLevelMass, inverseMoonMass, timeStep);

        if (impulse.length() > 0.0D) {
            final Vector3d worldImpulse = new Vector3d(impulse.x(), impulse.y(), impulse.z());
            final Vector3d onSubLevel = subLevel.logicalPose().transformNormalInverse(worldImpulse, new Vector3d());
            final Vector3d onMoon = shim.logicalPose()
                    .transformNormalInverse(new Vector3d(worldImpulse).negate(), new Vector3d());
            pipeline.applyImpulse(subLevel, subLevelAnchor, onSubLevel);
            pipeline.applyImpulse(shim, moonPlotAnchor, onMoon);
        }

        final double subLevelScale = ScaleState.getServerScale(subLevel);
        if (this.pocket$moonPairConstraint == null
                || !this.pocket$moonPairConstraint.isValid()
                || this.pocket$constraintMoonRuntimeId != moon.getRuntimeId()
                || Math.abs(this.pocket$constraintSubLevelScale - subLevelScale) > 1.0E-6D) {
            this.pocket$removePairConstraint();
            this.pocket$moonPairConstraint = pipeline.addConstraint(
                    subLevel,
                    shim,
                    new FreeConstraintConfiguration(subLevelAnchor, moonPlotAnchor, new org.joml.Quaterniond())
            );
            this.pocket$constraintMoonRuntimeId = moon.getRuntimeId();
            this.pocket$constraintSubLevelScale = subLevelScale;
            pocket$configurePlungerConstraint(this.pocket$moonPairConstraint);
        }

        ci.cancel();
    }

    @Inject(method = "remove", at = @At("HEAD"), remap = false, require = 0)
    private void pocket$removeMoonPair(final Entity.RemovalReason reason, final CallbackInfo ci) {
        this.pocket$removePairConstraint();
        this.pocket$moonAttached = false;
    }

    @Unique
    private void pocket$tickWorldPair(final Entity self, final ServerLevel level) {
        final Entity other = AeronauticsPlungerAccess.getOther(self);
        if (other == null || !AeronauticsPlungerAccess.isPlunged(other)) {
            this.pocket$removePairConstraint();
            return;
        }

        if (other instanceof final MoonPlungerAttachment moonAttachment && moonAttachment.pocket$isMoonAttached()) {
            this.pocket$removePairConstraint();
            return;
        }

        if (Sable.HELPER.getContaining(other) != null) {
            this.pocket$removePairConstraint();
            return;
        }

        final BoxPhysicsObject moon = MoonPhysicsTarget.body(level);
        final Vector3d moonAnchor = this.pocket$moonLocalAnchor(level);
        final Vec3 moonWorldAnchor = this.pocket$moonWorldAnchor(level);
        if (moon == null || moonAnchor == null || moonWorldAnchor == null) {
            this.pocket$removePairConstraint();
            return;
        }

        final MoonSubLevel shim = MoonSubLevels.get(level);
        if (shim == null) {
            this.pocket$removePairConstraint();
            return;
        }
        shim.syncFromBody();
        final Vector3d moonPlotAnchor = new Vector3d(moonAnchor);

        final PhysicsPipeline pipeline = SubLevelPhysicsSystem.require(level).getPipeline();
        final Vec3 worldAnchor = AeronauticsPlungerAccess.getAttachmentPos(other);

        final Vector3d pull = JOMLConversion.toJOML(worldAnchor.subtract(moonWorldAnchor));
        final Vector3d localMoonPull = shim.logicalPose().transformNormalInverse(pull, new Vector3d());
        final double inverseMoonMass = pocket$inverseNormalMass(
                shim.getMassTracker(), moonPlotAnchor, localMoonPull);

        // Far end is anchored to the world, so only the moon is pulled.
        final MoonPlungerSpring.Impulse impulse = MoonPlungerSpring.impulse(
                pull.x, pull.y, pull.z, inverseMoonMass, -1.0D, 1.0D / 20.0D);

        if (impulse.length() > 0.0D) {
            final Vector3d onMoon = shim.logicalPose().transformNormalInverse(
                    new Vector3d(impulse.x(), impulse.y(), impulse.z()), new Vector3d());
            pipeline.applyImpulse(shim, moonPlotAnchor, onMoon);
        }

        if (this.pocket$moonPairConstraint == null
                || !this.pocket$moonPairConstraint.isValid()
                || this.pocket$constraintMoonRuntimeId != moon.getRuntimeId()) {
            this.pocket$removePairConstraint();
            this.pocket$moonPairConstraint = pipeline.addConstraint(
                    shim,
                    null,
                    new FreeConstraintConfiguration(moonPlotAnchor, JOMLConversion.toJOML(worldAnchor), new org.joml.Quaterniond())
            );
            this.pocket$constraintMoonRuntimeId = moon.getRuntimeId();
            this.pocket$constraintSubLevelScale = 1.0D;
            pocket$configurePlungerConstraint(this.pocket$moonPairConstraint);
        }
    }

    @Unique
    private static double pocket$inverseNormalMass(
            final MassData mass,
            final Vector3d anchor,
            final Vector3d direction
    ) {
        if (mass == null || mass.isInvalid()) return -1.0D;
        if (direction.lengthSquared() < 1.0E-12D) return mass.getInverseMass();
        return mass.getInverseNormalMass(anchor, direction);
    }

    @Unique
    private static void pocket$configurePlungerConstraint(final PhysicsConstraintHandle constraint) {
        if (constraint == null) return;
        for (final ConstraintJointAxis axis : ConstraintJointAxis.LINEAR) {
            constraint.setMotor(axis, 0.0D, 0.0D, 1.0D, false, 50.0D);
        }
        for (final ConstraintJointAxis axis : ConstraintJointAxis.ANGULAR) {
            constraint.setMotor(axis, 0.0D, 0.0D, 0.25D, false, 50.0D);
        }
    }

    @Unique
    private void pocket$updateMoonPosition(final Entity self, final ServerLevel level) {
        final Vec3 anchor = this.pocket$moonWorldAnchor(level);
        final Vector3d worldNormal = MoonPhysicsTarget.worldNormal(level, this.pocket$moonNormal);
        if (anchor == null || worldNormal == null) return;

        final Direction direction = pocket$nearestDirection(worldNormal);
        AeronauticsPlungerAccess.setDirection(self, direction);
        self.setPos(
                anchor.x - direction.getStepX() * 0.6D,
                anchor.y - direction.getStepY() * 0.6D,
                anchor.z - direction.getStepZ() * 0.6D
        );
    }

    @Unique
    private void pocket$detachMoon(final Entity self) {
        this.pocket$moonAttached = false;
        this.pocket$removePairConstraint();
        self.noPhysics = false;
        AeronauticsPlungerAccess.setPlunged(self, false);
        self.setDeltaMovement(Vec3.ZERO);
    }

    @Unique
    private void pocket$removePairConstraint() {
        if (this.pocket$moonPairConstraint != null) {
            this.pocket$moonPairConstraint.remove();
            this.pocket$moonPairConstraint = null;
        }
        this.pocket$constraintMoonRuntimeId = -1;
        this.pocket$constraintSubLevelScale = 1.0D;
    }

    @Unique
    private static Direction pocket$nearestDirection(final Vector3dc normal) {
        Direction best = Direction.UP;
        double bestDot = -Double.MAX_VALUE;
        for (final Direction direction : Direction.values()) {
            final double dot = normal.x() * direction.getStepX()
                    + normal.y() * direction.getStepY()
                    + normal.z() * direction.getStepZ();
            if (dot > bestDot) {
                bestDot = dot;
                best = direction;
            }
        }
        return best;
    }

    @Override
    public boolean pocket$isMoonAttached() {
        return this.pocket$moonAttached;
    }

    @Override
    public Vector3dc pocket$moonNormalizedAnchor() {
        return this.pocket$moonAttached ? this.pocket$moonAnchor : null;
    }

    @Override
    public Vector3d pocket$moonLocalAnchor(final ServerLevel level) {
        return this.pocket$moonAttached ? MoonPhysicsTarget.localAnchor(level, this.pocket$moonAnchor) : null;
    }

    @Override
    public Vec3 pocket$moonWorldAnchor(final ServerLevel level) {
        return this.pocket$moonAttached ? MoonPhysicsTarget.worldAnchor(level, this.pocket$moonAnchor) : null;
    }
}
