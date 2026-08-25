package com.misterblusky9.pocket.physics;

public final class ScaledRebuildCollisionEffectFilterTest {
    private static final long SCENE = 0x5CA1EL;
    private static final long OTHER_SCENE = 0x5CA1FL;
    private static final int SCALED_BODY = 17;

    public static void main(final String[] args) {
        suppressesEveryRebuildFrameAcrossRepeatedTransitions();
        preservesStableAndUnrelatedCollisionEffects();
        keepsScenesIndependent();
        System.out.println("ScaledRebuildCollisionEffectFilterTest: PASS (64 repeated scale transitions)");
    }

    private static void suppressesEveryRebuildFrameAcrossRepeatedTransitions() {
        for (int transition = 0; transition < 64; transition++) {
            final long tick = 1_000L + transition * 14L;

            for (int frame = 0; frame < 12; frame++) {
                final long frameTick = tick + frame;
                ScaledRebuildCollisionEffectFilter.markRebuilt(SCENE, SCALED_BODY, frameTick);
                final double[] filtered = ScaledRebuildCollisionEffectFilter.filter(
                        SCENE,
                        frameTick,
                        records(record(SCALED_BODY, -1), record(80, 81))
                );
                check(filtered.length == ScaledRebuildCollisionEffectFilter.RECORD_WIDTH,
                        "rebuild contact leaked on transition " + transition + ", frame " + frame);
                check((int) filtered[0] == 80 && (int) filtered[1] == 81,
                        "unrelated collision was not preserved");
            }

            final long settledTick = tick + 12L;
            final double[] settled = ScaledRebuildCollisionEffectFilter.filter(
                    SCENE, settledTick, record(SCALED_BODY, -1));
            check(settled.length == ScaledRebuildCollisionEffectFilter.RECORD_WIDTH,
                    "stable collision remained muted after transition " + transition);
        }
    }

    private static void preservesStableAndUnrelatedCollisionEffects() {
        final long tick = 9_000L;
        final double[] collisions = records(record(SCALED_BODY, -1), record(40, 41));
        check(ScaledRebuildCollisionEffectFilter.filter(SCENE, tick, collisions) == collisions,
                "no-op filter needlessly replaced the native result");

        ScaledRebuildCollisionEffectFilter.markRebuilt(SCENE, SCALED_BODY, tick + 1L);
        final double[] artifactAfterRealContact = ScaledRebuildCollisionEffectFilter.filter(
                SCENE, tick + 1L, records(record(40, 41), record(-1, SCALED_BODY)));
        check(artifactAfterRealContact.length == ScaledRebuildCollisionEffectFilter.RECORD_WIDTH
                        && (int) artifactAfterRealContact[0] == 40,
                "record before a rebuild artifact was corrupted");

        final double[] malformed = { SCALED_BODY, -1.0D, 25.0D };
        check(ScaledRebuildCollisionEffectFilter.filter(SCENE, tick + 1L, malformed) == malformed,
                "malformed future Sable record format should pass through safely");
    }

    private static void keepsScenesIndependent() {
        final long tick = 10_000L;
        ScaledRebuildCollisionEffectFilter.markRebuilt(SCENE, SCALED_BODY, tick);
        final double[] otherSceneCollision = record(SCALED_BODY, -1);
        check(ScaledRebuildCollisionEffectFilter.filter(OTHER_SCENE, tick, otherSceneCollision)
                        == otherSceneCollision,
                "body id from another scene was muted");
        ScaledRebuildCollisionEffectFilter.forgetScene(SCENE);
    }

    private static double[] record(final int firstBody, final int secondBody) {
        final double[] record = new double[ScaledRebuildCollisionEffectFilter.RECORD_WIDTH];
        record[0] = firstBody;
        record[1] = secondBody;
        record[2] = 1_000.0D;
        return record;
    }

    private static double[] records(final double[]... records) {
        final double[] joined = new double[records.length * ScaledRebuildCollisionEffectFilter.RECORD_WIDTH];
        int offset = 0;
        for (final double[] record : records) {
            System.arraycopy(record, 0, joined, offset, record.length);
            offset += record.length;
        }
        return joined;
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }

    private ScaledRebuildCollisionEffectFilterTest() {
    }
}
