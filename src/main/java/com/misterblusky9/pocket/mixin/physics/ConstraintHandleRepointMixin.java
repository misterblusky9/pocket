package com.misterblusky9.pocket.mixin.physics;

import com.misterblusky9.pocket.physics.RepointableConstraint;
import com.misterblusky9.pocket.physics.RapierSceneLifetime;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.constraint.RapierConstraintHandle", remap = false)
public abstract class ConstraintHandleRepointMixin implements RepointableConstraint {
    @Mutable
    @Shadow
    @Final
    protected long handle;

    @Shadow
    @Final
    protected long sceneHandle;

    @Unique
    private RapierSceneLifetime.Token pocket$sceneToken;

    @Unique
    private boolean pocket$knownRemoved;

    @Unique
    private RepointableConstraint.Motor[] pocket$motors;

    @Unique
    private boolean pocket$replayingMotors;

    @Unique
    private boolean pocket$contactsEnabled = true;

    @Unique
    private boolean pocket$replayingContacts;

    @Shadow
    public abstract boolean isValid();

    @Shadow
    public abstract void setMotor(
            ConstraintJointAxis axis,
            double target,
            double stiffness,
            double damping,
            boolean hasForceLimit,
            double maxForce
    );

    @Shadow
    public abstract void setContactsEnabled(boolean enabled);

    @Inject(method = "setMotor", at = @At("RETURN"), remap = false)
    private void pocket$captureMotor(
            final ConstraintJointAxis axis,
            final double target,
            final double stiffness,
            final double damping,
            final boolean hasForceLimit,
            final double maxForce,
            final CallbackInfo ci
    ) {
        if (this.pocket$replayingMotors || axis == null) return;

        if (this.pocket$motors == null) {
            this.pocket$motors = new RepointableConstraint.Motor[ConstraintJointAxis.values().length];
        }
        this.pocket$motors[axis.ordinal()] =
                new RepointableConstraint.Motor(target, stiffness, damping, hasForceLimit, maxForce);
    }

    @Override
    public void pocket$replayMotors() {
        if (this.pocket$motors == null || !this.isValid()) return;

        final ConstraintJointAxis[] axes = ConstraintJointAxis.values();

        this.pocket$replayingMotors = true;
        try {
            for (int axis = 0; axis < this.pocket$motors.length; axis++) {
                final RepointableConstraint.Motor motor = this.pocket$motors[axis];
                if (motor == null) continue;

                this.setMotor(
                        axes[axis],
                        motor.target(),
                        motor.stiffness(),
                        motor.damping(),
                        motor.hasForceLimit(),
                        motor.maxForce());
            }
        } finally {
            this.pocket$replayingMotors = false;
        }
    }

    @Inject(method = "setContactsEnabled", at = @At("RETURN"), remap = false)
    private void pocket$captureContacts(final boolean enabled, final CallbackInfo ci) {
        if (!this.pocket$replayingContacts) this.pocket$contactsEnabled = enabled;
    }

    @Override
    public void pocket$replayContacts() {
        if (!this.isValid()) return;
        this.pocket$replayingContacts = true;
        try {
            this.setContactsEnabled(this.pocket$contactsEnabled);
        } finally {
            this.pocket$replayingContacts = false;
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void pocket$captureSceneGeneration(
            final long sceneHandle,
            final long nativeHandle,
            final CallbackInfo ci
    ) {
        this.pocket$sceneToken = RapierSceneLifetime.tokenFor(sceneHandle);
    }

    @Override
    public long pocket$nativeHandle() {
        return this.handle;
    }

    @Override
    public long pocket$sceneHandle() {
        return this.sceneHandle;
    }

    @Override
    public boolean pocket$isSceneLive() {
        return RapierSceneLifetime.isLive(this.pocket$sceneToken, this.sceneHandle);
    }

    @Override
    public boolean pocket$isKnownRemoved() {
        return this.pocket$knownRemoved;
    }

    @Override
    public void pocket$markRemoved() {
        this.pocket$knownRemoved = true;
    }

    @Override
    public void pocket$repoint(final long nativeHandle) {
        this.handle = nativeHandle;
        this.pocket$knownRemoved = false;
    }
}
