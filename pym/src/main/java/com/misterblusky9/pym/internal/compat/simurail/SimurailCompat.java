package com.misterblusky9.pym.internal.compat.simurail;

import com.misterblusky9.pym.api.spi.JointConductor;
import com.misterblusky9.pym.internal.config.PymConfig;
import com.misterblusky9.pym.internal.extension.PymExtensions;

import java.util.Set;

public final class SimurailCompat {
    private static boolean present;

    public static boolean enabled() {
        return present && PymConfig.simurailSupport();
    }

    public static void init() {
        present = true;
        PymExtensions.register((JointConductor) joint ->
                !enabled() || !SimurailJointLimits.isRailGuide(joint.handle()));
        PymExtensions.ownership(track -> enabled() ? SimurailTrackOwnership.carriagesOnTrack(track) : Set.of());
    }

    private SimurailCompat() {}
}
