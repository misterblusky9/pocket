package com.misterblusky9.pym.internal.compat.simulated;

import com.misterblusky9.pym.internal.extension.PymExtensions;

public final class SimulatedCompat {
    public static final String ROPE_BLOCKS_SCALING = "Remove rope before scaling";

    public static void init() {
        PymExtensions.register((subLevel, from, to) ->
                SimulatedRopeScaleBoundary.blocksTransition(subLevel, to) ? ROPE_BLOCKS_SCALING : null);
        PymExtensions.ownership(SwivelBearingHeads::of);
    }

    private SimulatedCompat() {}
}
