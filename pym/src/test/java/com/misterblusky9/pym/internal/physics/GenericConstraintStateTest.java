package com.misterblusky9.pym.internal.physics;

import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class GenericConstraintStateTest {
    public static void main(final String[] args) {
        ownsFramesAndCreationAxes();
        movingOneFrameDoesNotHideTheOther();
        latestAnchorControlsRefresh();
        latestLimitsSurviveRepeatedRebuilds();
        replayFailureReleasesCaptureGuard();
        sameScaleJointConvertsLinearLimits();
        rescalingBothEndsReducesLinearLimits();
        crossScaleJointLeavesLinearLimitsAlone();
        angularLimitsAreNeverScaled();
        lockedAxesSurviveRebuild();
        everythingSurvivesSuccessiveResizes();
        System.out.println("GenericConstraintStateTest: PASS");
    }

    private static void ownsFramesAndCreationAxes() {
        final Vector3d position = new Vector3d(103, 4, 5);
        final Quaterniond rotation = new Quaterniond().rotationY(0.4);
        final Quaterniond expectedRotation = new Quaterniond(rotation);
        final Set<ConstraintJointAxis> axes = new HashSet<>(Set.of(ConstraintJointAxis.LINEAR_X));
        final GenericConstraintState state = new GenericConstraintState(new GenericConstraintConfiguration(
                position, new Vector3d(200, 0, 0), rotation, new Quaterniond(), axes),
                new Vector3d(100, 0, 0), 1, new Vector3d(200, 0, 0), 1);
        position.zero();
        rotation.identity();
        axes.clear();
        check(state.configuration().pos1().equals(new Vector3d(103, 4, 5)), "creation position was aliased");
        check(state.configuration().orientation1().equals(expectedRotation), "creation rotation was aliased");
        check(state.configuration().lockedAxes().contains(ConstraintJointAxis.LINEAR_X), "creation axes were aliased");

        position.set(207, 8, 9);
        rotation.rotationX(0.7);
        expectedRotation.set(rotation);
        state.captureFrame(false, position, rotation, new Vector3d(200, 0, 0), 0.0625);
        position.zero();
        rotation.identity();
        check(state.configuration().pos2().equals(new Vector3d(207, 8, 9)), "updated position was aliased or scaled");
        check(state.configuration().orientation2().equals(expectedRotation), "updated rotation was aliased");
        check(state.configuration().pos1().equals(new Vector3d(103, 4, 5)), "other frame was changed");

        state.captureFrame(true, new Vector3d(105, 6, 7), expectedRotation, null, 1);
        check(state.configuration().pos1().equals(new Vector3d(105, 6, 7)), "world frame was not retained");
        check(state.configuration().orientation1().equals(expectedRotation), "frame 1 rotation was lost");
    }

    private static void movingOneFrameDoesNotHideTheOther() {
        final Vector3d pivot1 = new Vector3d(100, 0, 0);
        final Vector3d pivot2 = new Vector3d(200, 0, 0);
        final GenericConstraintState state = state(new Vector3d(102, 0, 0), new Vector3d(203, 0, 0), pivot1, pivot2);
        state.captureFrame(true, new Vector3d(104, 0, 0), new Quaterniond(), pivot1, 0.5);
        check(state.anchorsWouldMove(pivot1, 0.5, pivot2, 0.5), "frame 1 update hid stale frame 2");
        state.captureFrame(false, new Vector3d(205, 0, 0), new Quaterniond(), pivot2, 0.5);
        check(!state.anchorsWouldMove(pivot1, 0.5, pivot2, 0.5), "fresh frames caused an unnecessary rebuild");

        pivot1.x += 1;
        check(state.anchorsWouldMove(pivot1, 0.5, pivot2, 0.5), "pivot snapshot was aliased");
        state.captureFrame(false, new Vector3d(206, 0, 0), new Quaterniond(), pivot2, 0.5);
        check(state.anchorsWouldMove(pivot1, 0.5, pivot2, 0.5), "frame 2 update hid stale frame 1");
        state.bake(pivot1, 0.5, pivot2, 0.5);
        check(!state.anchorsWouldMove(pivot1, 0.5, pivot2, 0.5), "rebuild did not refresh both baselines");
    }

    private static void latestAnchorControlsRefresh() {
        final Vector3d pivot = new Vector3d(100, 0, 0);
        final GenericConstraintState state = state(pivot, new Vector3d(3, 4, 5), pivot, null);
        check(!state.anchorsWouldMove(pivot, 0.0625, null, 1), "an anchor at the pivot moved");
        state.captureFrame(true, new Vector3d(116, 0, 0), new Quaterniond(), pivot, 1);
        for (final double scale : new double[]{0.5, 0.0625, 1, 0.0625, 1}) {
            check(state.anchorsWouldMove(pivot, scale, null, 1), "refresh used the original anchor");
            state.bake(pivot, scale, null, 1);
            check(state.configuration().pos1().x() == 116, "rebuild compounded scale in the stored anchor");
            check(state.configuration().pos2().equals(new Vector3d(3, 4, 5)), "world endpoint moved");
            check(!state.anchorsWouldMove(pivot, scale, null, 1), "rebuilt frame stayed stale");
        }
    }

    private static void latestLimitsSurviveRepeatedRebuilds() {
        final GenericConstraintState state = state(new Vector3d(), new Vector3d(), null, null);
        final RecordingHandle handle = new RecordingHandle(state);
        state.captureLimit(ConstraintJointAxis.LINEAR_X, 1, 2);
        state.captureLimit(ConstraintJointAxis.LINEAR_X, -0.125, 0.375);
        state.captureLimit(ConstraintJointAxis.LINEAR_Y, -0.0625, Float.MAX_VALUE);
        state.captureLimit(ConstraintJointAxis.ANGULAR_Y, -0.75, 0.75);
        state.captureLimit(ConstraintJointAxis.ANGULAR_Z, 0, 0);
        for (int i = 0; i < 3; i++) {
            handle.limits.clear();
            state.replayLimits(handle, 1, 1);
            check(handle.limits.size() == 4, "unset axes were replayed or a limit was lost");
            check(handle.limits.get(ConstraintJointAxis.LINEAR_X).equals(new Bounds(-0.125, 0.375)), "latest bounds were lost");
            check(handle.limits.get(ConstraintJointAxis.LINEAR_Y).equals(new Bounds(-0.0625, Float.MAX_VALUE)), "open bound changed");
            check(handle.limits.get(ConstraintJointAxis.ANGULAR_Y).equals(new Bounds(-0.75, 0.75)), "angular limit was scaled");
            check(handle.limits.get(ConstraintJointAxis.ANGULAR_Z).equals(new Bounds(0, 0)), "zero limit was lost");
        }
        handle.valid = false;
        handle.limits.clear();
        state.replayLimits(handle, 1, 1);
        check(handle.limits.isEmpty(), "invalid handle was mutated");
    }

    private static void replayFailureReleasesCaptureGuard() {
        final GenericConstraintState state = state(new Vector3d(), new Vector3d(), null, null);
        final RecordingHandle handle = new RecordingHandle(state);
        state.captureLimit(ConstraintJointAxis.LINEAR_X, 1, 2);
        handle.fail = true;
        try {
            state.replayLimits(handle, 1, 1);
            throw new AssertionError("setter failure was swallowed");
        } catch (final IllegalStateException expected) {
            handle.fail = false;
        }
        state.captureLimit(ConstraintJointAxis.LINEAR_X, 3, 4);
        state.replayLimits(handle, 1, 1);
        check(handle.limits.get(ConstraintJointAxis.LINEAR_X).equals(new Bounds(3, 4)), "replay guard remained set");
    }

    private static void sameScaleJointConvertsLinearLimits() {
        check(GenericConstraintState.toMetricLimit(ConstraintJointAxis.LINEAR_X, 1, 1, 1) == 1,
                "1x/1x linear limit was not left alone");
        check(GenericConstraintState.toMetricLimit(ConstraintJointAxis.LINEAR_X, -1, 1, 1) == -1,
                "1x/1x linear limit was not left alone");

        for (final ConstraintJointAxis axis : ConstraintJointAxis.LINEAR) {
            check(GenericConstraintState.toMetricLimit(axis, 1, 0.125, 0.125) == 0.125,
                    "1/8 joint did not contract " + axis);
            check(GenericConstraintState.toMetricLimit(axis, -1, 0.125, 0.125) == -0.125,
                    "1/8 joint did not contract " + axis);
        }

        final GenericConstraintState state = state(new Vector3d(), new Vector3d(), null, null);
        final RecordingHandle handle = new RecordingHandle(state);
        state.captureLimit(ConstraintJointAxis.LINEAR_X, -1, 1);

        state.replayLimits(handle, 1, 1);
        check(handle.limits.get(ConstraintJointAxis.LINEAR_X).equals(new Bounds(-1, 1)),
                "1x/1x replay changed the limit");

        handle.limits.clear();
        state.replayLimits(handle, 0.125, 0.125);
        check(handle.limits.get(ConstraintJointAxis.LINEAR_X).equals(new Bounds(-0.125, 0.125)),
                "1/8 replay did not contract the limit");
    }

    private static void rescalingBothEndsReducesLinearLimits() {
        final GenericConstraintState state = state(new Vector3d(), new Vector3d(), null, null);
        final RecordingHandle handle = new RecordingHandle(state);
        state.captureLimit(ConstraintJointAxis.LINEAR_Z, -1, 1);

        for (final double scale : new double[]{0.125, 0.125, 1, 0.0625, 1}) {
            handle.limits.clear();
            state.replayLimits(handle, scale, scale);
            check(handle.limits.get(ConstraintJointAxis.LINEAR_Z).equals(new Bounds(-scale, scale)),
                    "replay at " + scale + " compounded or lost the nominal limit");
        }
    }

    private static void crossScaleJointLeavesLinearLimitsAlone() {
        for (final ConstraintJointAxis axis : ConstraintJointAxis.LINEAR) {
            final double forwards = GenericConstraintState.toMetricLimit(axis, 1, 1, 0.125);
            final double backwards = GenericConstraintState.toMetricLimit(axis, 1, 0.125, 1);
            check(forwards == 1, "cross-scale joint inherited an endpoint scale on " + axis);
            check(forwards == backwards, "cross-scale result depended on endpoint order on " + axis);
        }

        final GenericConstraintState state = state(new Vector3d(), new Vector3d(), null, null);
        final RecordingHandle handle = new RecordingHandle(state);
        state.captureLimit(ConstraintJointAxis.LINEAR_X, -1, 1);

        state.replayLimits(handle, 1, 0.125);
        final Bounds forwards = handle.limits.get(ConstraintJointAxis.LINEAR_X);
        handle.limits.clear();
        state.replayLimits(handle, 0.125, 1);
        final Bounds backwards = handle.limits.get(ConstraintJointAxis.LINEAR_X);

        check(forwards.equals(new Bounds(-1, 1)), "cross-scale replay rescaled the limit");
        check(forwards.equals(backwards), "cross-scale replay depended on endpoint order");
    }

    private static void angularLimitsAreNeverScaled() {
        final double min = -0.75;
        final double max = 2.5;
        final double[][] pairs = {{1, 1}, {0.125, 0.125}, {1, 0.125}, {0.125, 1}, {0.0625, 0.0625}};

        for (final ConstraintJointAxis axis : ConstraintJointAxis.ANGULAR) {
            for (final double[] pair : pairs) {
                check(GenericConstraintState.limitScale(axis, pair[0], pair[1]) == 1.0D,
                        "angular axis " + axis + " picked up a scale");
                identical(GenericConstraintState.toMetricLimit(axis, min, pair[0], pair[1]), min, axis);
                identical(GenericConstraintState.toMetricLimit(axis, max, pair[0], pair[1]), max, axis);
            }
        }

        final GenericConstraintState state = state(new Vector3d(), new Vector3d(), null, null);
        final RecordingHandle handle = new RecordingHandle(state);
        state.captureLimit(ConstraintJointAxis.ANGULAR_Y, min, max);
        for (final double[] pair : pairs) {
            handle.limits.clear();
            state.replayLimits(handle, pair[0], pair[1]);
            final Bounds replayed = handle.limits.get(ConstraintJointAxis.ANGULAR_Y);
            identical(replayed.min(), min, ConstraintJointAxis.ANGULAR_Y);
            identical(replayed.max(), max, ConstraintJointAxis.ANGULAR_Y);
        }
    }

    private static void lockedAxesSurviveRebuild() {
        final GenericConstraintState state = state(new Vector3d(), new Vector3d(), null, null);
        final RecordingHandle handle = new RecordingHandle(state);

        state.replayLockedAxes(handle);
        check(handle.locked == null, "an untouched joint replayed a lock mask");

        handle.lockAxes(ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.ANGULAR_Z);
        handle.locked = null;
        state.replayLockedAxes(handle);
        check(Set.of(ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.ANGULAR_Z).equals(handle.locked),
                "runtime lock mask was lost on rebuild");

        handle.lockAxes(ConstraintJointAxis.LINEAR_Y);
        handle.locked = null;
        state.replayLockedAxes(handle);
        check(Set.of(ConstraintJointAxis.LINEAR_Y).equals(handle.locked),
                "replay merged masks instead of replacing");

        handle.lockAxes();
        handle.locked = null;
        state.replayLockedAxes(handle);
        check(handle.locked != null && handle.locked.isEmpty(),
                "unlocking every axis was mistaken for never having locked one");

        handle.valid = false;
        handle.locked = null;
        state.replayLockedAxes(handle);
        check(handle.locked == null, "invalid handle was mutated");
    }

    private static void everythingSurvivesSuccessiveResizes() {
        final Vector3d pivot = new Vector3d(100, 0, 0);
        final GenericConstraintState state = state(new Vector3d(104, 0, 0), new Vector3d(3, 4, 5), pivot, null);
        final RecordingHandle handle = new RecordingHandle(state);

        state.captureFrame(true, new Vector3d(116, 2, 0), new Quaterniond().rotationY(0.4), pivot, 1);
        state.captureFrame(false, new Vector3d(7, 8, 9), new Quaterniond(), null, 1);
        state.captureLimit(ConstraintJointAxis.LINEAR_X, -2, 2);
        state.captureLimit(ConstraintJointAxis.ANGULAR_X, -0.5, 0.5);
        handle.lockAxes(ConstraintJointAxis.LINEAR_Z);

        final Quaterniond expected = new Quaterniond().rotationY(0.4);

        for (final double scale : new double[]{0.5, 0.125, 1, 0.0625, 1, 0.125}) {
            handle.limits.clear();
            handle.locked = null;

            state.bake(pivot, scale, null, 1);
            state.replayLimits(handle, scale, 1);
            state.replayLockedAxes(handle);

            check(state.configuration().pos1().equals(new Vector3d(116, 2, 0)),
                    "frame 1 drifted at scale " + scale);
            check(state.configuration().orientation1().equals(expected),
                    "frame 1 rotation drifted at scale " + scale);
            check(state.configuration().pos2().equals(new Vector3d(7, 8, 9)),
                    "frame 2 drifted at scale " + scale);
            check(!state.anchorsWouldMove(pivot, scale, null, 1),
                    "rebuilt frames stayed stale at scale " + scale);
            check(handle.limits.get(ConstraintJointAxis.LINEAR_X).equals(new Bounds(-2, 2)),
                    "cross-scale linear limit moved at scale " + scale);
            check(handle.limits.get(ConstraintJointAxis.ANGULAR_X).equals(new Bounds(-0.5, 0.5)),
                    "angular limit moved at scale " + scale);
            check(Set.of(ConstraintJointAxis.LINEAR_Z).equals(handle.locked),
                    "lock mask was lost at scale " + scale);
        }

        state.bake(pivot, 0.125, null, 0.125);
        handle.limits.clear();
        state.replayLimits(handle, 0.125, 0.125);
        check(handle.limits.get(ConstraintJointAxis.LINEAR_X).equals(new Bounds(-0.25, 0.25)),
                "same-scale pair did not contract the stored nominal limit");
        check(handle.limits.get(ConstraintJointAxis.ANGULAR_X).equals(new Bounds(-0.5, 0.5)),
                "angular limit followed the linear conversion");
    }

    private static void identical(final double actual, final double expected, final ConstraintJointAxis axis) {
        check(Double.doubleToRawLongBits(actual) == Double.doubleToRawLongBits(expected),
                "angular limit on " + axis + " was not byte-for-byte unchanged");
    }

    private static GenericConstraintState state(
            final Vector3dc position1, final Vector3dc position2, final Vector3dc pivot1, final Vector3dc pivot2
    ) {
        return new GenericConstraintState(new GenericConstraintConfiguration(
                position1, position2, new Quaterniond(), new Quaterniond(), Set.of()), pivot1, 1, pivot2, 1);
    }

    private record Bounds(double min, double max) {}

    private static final class RecordingHandle implements GenericConstraintHandle {
        private final GenericConstraintState state;
        private final Map<ConstraintJointAxis, Bounds> limits = new EnumMap<>(ConstraintJointAxis.class);
        private Set<ConstraintJointAxis> locked;
        private boolean valid = true;
        private boolean fail;

        private RecordingHandle(final GenericConstraintState state) { this.state = state; }

        @Override
        public void setLimit(final ConstraintJointAxis axis, final double min, final double max) {
            if (this.fail) throw new IllegalStateException("test setter failure");
            this.limits.put(axis, new Bounds(min, max));
            this.state.captureLimit(axis, min, max);
        }

        @Override public boolean isValid() { return this.valid; }
        @Override public void setFrame1(final Vector3dc position, final Quaterniondc orientation) {}
        @Override public void setFrame2(final Vector3dc position, final Quaterniondc orientation) {}
        @Override
        public void lockAxes(final ConstraintJointAxis... axes) {
            this.locked = Set.of(axes);
            this.state.captureLockedAxes(axes);
        }

        @Override public void getJointImpulses(final Vector3d linear, final Vector3d angular) {}
        @Override public void setContactsEnabled(final boolean enabled) {}
        @Override public void setMotor(final ConstraintJointAxis axis, final double target, final double stiffness,
                                       final double damping, final boolean hasForceLimit, final double maxForce) {}
        @Override public void remove() { this.valid = false; }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
