package com.misterblusky9.pym.internal.client;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.scale.ScaleState;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.Vector3dc;

import java.util.ArrayDeque;

public final class ParticleEmitterSource {
    private static final ThreadLocal<ArrayDeque<Double>> SCOPES = new ThreadLocal<>();

    public static double currentScale() {
        ArrayDeque<Double> scopes = SCOPES.get();
        return scopes == null || scopes.isEmpty() ? Double.NaN : scopes.peek();
    }

    public static Scope fromEntity(Entity entity) {
        return push(entity == null ? 1.0D : Pym.entities().scaleOf(entity));
    }

    public static Scope fromBlock(Level level, BlockPos position) {
        if (!(level instanceof ClientLevel) || position == null) return push(1.0D);
        SubLevel subLevel = Sable.HELPER.getContaining(level, position);
        return push(subLevel instanceof ClientSubLevel clientSubLevel
                && !clientSubLevel.isRemoved() ? scaleOf(clientSubLevel) : 1.0D);
    }

    public static Scope push(double emitterScale) {
        ArrayDeque<Double> scopes = SCOPES.get();
        if (scopes == null) {
            scopes = new ArrayDeque<>();
            SCOPES.set(scopes);
        }
        scopes.push(ScaleBounds.isValid(emitterScale) ? emitterScale : 1.0D);
        return new Scope();
    }

    public static double scaleOf(ClientSubLevel subLevel) {
        double trackedScale = ScaleState.getClientScale(subLevel);
        if (ScaleBounds.isValid(trackedScale)
                && (ScaleState.hasClientSnapshot(subLevel.getUniqueId())
                || !ScaleBounds.same(trackedScale, 1.0D))) {
            return trackedScale;
        }

        Vector3dc renderScale = subLevel.renderPose().scale();
        if (Math.abs(renderScale.x() - renderScale.y()) > ScaleBounds.EPSILON
                || Math.abs(renderScale.x() - renderScale.z()) > ScaleBounds.EPSILON) {
            return 1.0D;
        }
        return ScaleBounds.isValid(renderScale.x()) ? renderScale.x() : 1.0D;
    }

    public static final class Scope implements AutoCloseable {
        private boolean closed;

        private Scope() {}

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            ArrayDeque<Double> scopes = SCOPES.get();
            if (scopes == null || scopes.isEmpty()) return;
            scopes.pop();
            if (scopes.isEmpty()) SCOPES.remove();
        }
    }

    private ParticleEmitterSource() {}
}
