package com.misterblusky9.pocket.explosion;

public final class BlastSuppressionTest {
    public static void main(final String[] args) throws InterruptedException {
        aScopeOpensAndClosesCleanly();
        aNestedBlastCannotCloseTheOuterScope();
        anUnbalancedExitCannotGoNegative();
        aThrownBlastLeavesNoResidue();
        scopesAreThreadLocal();
        System.out.println("BlastSuppressionTest: PASS");
    }

    private static void aScopeOpensAndClosesCleanly() {
        check(!BlastSuppression.active(), "suppression was already active");
        BlastSuppression.enter();
        check(BlastSuppression.active(), "entering did not suppress");
        BlastSuppression.exit();
        check(!BlastSuppression.active(), "exiting did not release suppression");
    }

    private static void aNestedBlastCannotCloseTheOuterScope() {
        BlastSuppression.enter();
        BlastSuppression.enter();
        BlastSuppression.exit();
        check(BlastSuppression.active(), "a nested blast released the outer scope early");
        BlastSuppression.exit();
        check(!BlastSuppression.active(), "the outer scope never released");
    }

    private static void anUnbalancedExitCannotGoNegative() {
        BlastSuppression.exit();
        BlastSuppression.exit();
        check(!BlastSuppression.active(), "a stray exit left suppression active");

        BlastSuppression.enter();
        check(BlastSuppression.active(), "the counter went negative and swallowed a scope");
        BlastSuppression.exit();
        check(!BlastSuppression.active(), "suppression outlived its scope");
    }

    private static void aThrownBlastLeavesNoResidue() {
        try {
            BlastSuppression.enter();
            try {
                throw new IllegalStateException("explode() blew up");
            } finally {
                BlastSuppression.exit();
            }
        } catch (final IllegalStateException expected) {
            // the mixin wraps explode() the same way
        }

        check(!BlastSuppression.active(), "a thrown explosion left suppression stuck on");
    }

    private static void scopesAreThreadLocal() throws InterruptedException {
        BlastSuppression.enter();

        final boolean[] seenOnOtherThread = { true };
        final Thread other = new Thread(() -> seenOnOtherThread[0] = BlastSuppression.active());
        other.start();
        other.join();

        check(!seenOnOtherThread[0], "suppression leaked onto another thread");
        BlastSuppression.exit();
        check(!BlastSuppression.active(), "suppression outlived its scope");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }

    private BlastSuppressionTest() {}
}
