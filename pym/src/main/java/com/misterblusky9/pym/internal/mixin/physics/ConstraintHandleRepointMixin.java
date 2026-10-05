package com.misterblusky9.pym.internal.mixin.physics;

import com.misterblusky9.pym.internal.physics.RepointableConstraint;
import com.misterblusky9.pym.internal.physics.RapierSceneLifetime;
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
    private RapierSceneLifetime.Token pym$sceneToken;

    @Unique
    private boolean pym$knownRemoved;

    @Unique
    private RepointableConstraint.Motor[] pym$motors;

    @Unique
    private boolean pym$replayingMotors;

    @Unique
    private boolean pym$contactsEnabled = true;

    @Unique
    private boolean pym$replayingContacts;

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
    private void pym$captureMotor(
            final ConstraintJointAxis axis,
            final double target,
            final double stiffness,
            final double damping,
            final boolean hasForceLimit,
            final double maxForce,
            final CallbackInfo ci
    ) {
        if (this.pym$replayingMotors || axis == null) return;

        if (this.pym$motors == null) {
            this.pym$motors = new RepointableConstraint.Motor[ConstraintJointAxis.values().length];
        }
        this.pym$motors[axis.ordinal()] =
                new RepointableConstraint.Motor(target, stiffness, damping, hasForceLimit, maxForce);
    }

    @Override
    public void pym$replayMotors() {
        if (this.pym$motors == null || !this.isValid()) return;

        final ConstraintJointAxis[] axes = ConstraintJointAxis.values();

        this.pym$replayingMotors = true;
        try {
            for (int axis = 0; axis < this.pym$motors.length; axis++) {
                final RepointableConstraint.Motor motor = this.pym$motors[axis];
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
            this.pym$replayingMotors = false;
        }
    }

    @Inject(method = "setContactsEnabled", at = @At("RETURN"), remap = false)
    private void pym$captureContacts(final boolean enabled, final CallbackInfo ci) {
        if (!this.pym$replayingContacts) this.pym$contactsEnabled = enabled;
    }

    @Override
    public void pym$replayContacts() {
        if (!this.isValid()) return;
        this.pym$replayingContacts = true;
        try {
            this.setContactsEnabled(this.pym$contactsEnabled);
        } finally {
            this.pym$replayingContacts = false;
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void pym$captureSceneGeneration(
            final long sceneHandle,
            final long nativeHandle,
            final CallbackInfo ci
    ) {
        this.pym$sceneToken = RapierSceneLifetime.tokenFor(sceneHandle);
    }

    @Override
    public long pym$nativeHandle() {
        return this.handle;
    }

    @Override
    public long pym$sceneHandle() {
        return this.sceneHandle;
    }

    @Override
    public boolean pym$isSceneLive() {
        return RapierSceneLifetime.isLive(this.pym$sceneToken, this.sceneHandle);
    }

    @Override
    public boolean pym$isKnownRemoved() {
        return this.pym$knownRemoved;
    }

    @Override
    public void pym$markRemoved() {
        this.pym$knownRemoved = true;
    }

    @Override
    public void pym$repoint(final long nativeHandle) {
        this.handle = nativeHandle;
        this.pym$knownRemoved = false;
    }
}
