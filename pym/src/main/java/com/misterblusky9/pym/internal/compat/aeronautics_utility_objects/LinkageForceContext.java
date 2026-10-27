package com.misterblusky9.pym.internal.compat.aeronautics_utility_objects;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.mixin.LinkageBodyAccess;
import com.misterblusky9.pym.internal.physics.InternalForceScaleContext;
import com.misterblusky9.pym.internal.physics.PairedForceScale;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

public final class LinkageForceContext {
    public static void apply(
            RigidBodyHandle handle, ForceTotal force,
            ServerSubLevel first, ServerSubLevel second, Operation<Void> original
    ) {
        if (!PairedForceScale.enter(first, second)) {
            apply(handle, force, original);
            return;
        }
        try {
            original.call(handle, force);
        } finally {
            PairedForceScale.exit();
        }
    }

    public static void apply(RigidBodyHandle handle, ForceTotal force, Operation<Void> original) {
        if (!(((LinkageBodyAccess) handle).pym$linkageBody() instanceof ServerSubLevel body)
                || body.isRemoved()) {
            original.call(handle, force);
            return;
        }
        InternalForceScaleContext.enter(body);
        try {
            original.call(handle, force);
        } finally {
            InternalForceScaleContext.exit(body);
        }
    }

    private LinkageForceContext() {}
}
