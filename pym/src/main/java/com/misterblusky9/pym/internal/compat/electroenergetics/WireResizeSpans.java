package com.misterblusky9.pym.internal.compat.electroenergetics;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.ConnectionEntry;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class WireResizeSpans {
    private record Span(InWorldNodeConnection connection, double before) {}

    private WireResizeSpans() {}

    static Object capture(ServerLevel level, Set<UUID> resizing) {
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        if (sd == null || sd.wireSimulationState == null) return null;
        List<Span> spans = new ArrayList<>();
        for (Map.Entry<InWorldNodeConnection, ConnectionEntry> entry : sd.wireSimulationState.getAllConnections()) {
            InWorldNodeConnection connection = entry.getKey();
            if (connection == null || entry.getValue() == null) continue;
            if (!touches(level, connection.node1(), resizing) && !touches(level, connection.node2(), resizing)) {
                continue;
            }
            double before = span(sd, connection);
            if (before > 0.0D) spans.add(new Span(connection, before));
        }
        return spans.isEmpty() ? null : new Object[]{sd, spans};
    }

    @SuppressWarnings("unchecked")
    static void apply(Object captured) {
        Object[] parts = (Object[]) captured;
        InfrastructureSavedData sd = (InfrastructureSavedData) parts[0];
        for (Span span : (List<Span>) parts[1]) {
            WireData data = sd.getConnectionData(span.connection());
            if (data == null || !(data.length > 0.0D)) continue;
            double after = span(sd, span.connection());
            if (!(after > 0.0D) || Math.abs(after - span.before()) < 1.0E-9D) continue;
            data.length *= after / span.before();
            sd.wireSimulationState.relocateConnection(span.connection(), data);
            sd.wireSync.handleWireAdded(span.connection(), data);
            sd.setDirty();
        }
    }

    private static boolean touches(ServerLevel level, InWorldNode node, Set<UUID> resizing) {
        if (node == null) return false;
        SubLevel containing = Sable.HELPER.getContaining(level, node.sourcePos());
        return containing != null && resizing.contains(containing.getUniqueId());
    }

    private static double span(InfrastructureSavedData sd, InWorldNodeConnection connection) {
        Vec3 a = sd.getNodePosition(connection.node1());
        Vec3 b = sd.getNodePosition(connection.node2());
        if (a == null || b == null) return -1.0D;
        double distance = a.distanceTo(b);
        return Double.isFinite(distance) ? distance : -1.0D;
    }
}
