package com.misterblusky9.pym.internal.scale;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;

record Pivot(Vector3dc world, UUID body, Vector3dc local) {
    static Pivot fixed(Vector3dc world) {
        return new Pivot(new Vector3d(world), null, null);
    }

    static Pivot on(ServerSubLevel body, Vector3dc local) {
        return new Pivot(null, body.getUniqueId(), new Vector3d(local));
    }

    Pivot detach(UUID removed, Pose3dc pose) {
        if (this.world != null || !this.body.equals(removed)) return this;
        return fixed(pose.transformPosition(new Vector3d(this.local)));
    }

    Vector3d resolve(ServerSubLevelContainer container, ServerSubLevel mover) {
        if (this.world != null) return new Vector3d(this.world);
        ServerSubLevel host = this.body.equals(mover.getUniqueId())
                ? mover
                : container.getSubLevel(this.body) instanceof ServerSubLevel found && !found.isRemoved()
                ? found
                : null;
        if (host == null) return new Vector3d(mover.logicalPose().position());
        return host.logicalPose().transformPosition(new Vector3d(this.local));
    }
}
