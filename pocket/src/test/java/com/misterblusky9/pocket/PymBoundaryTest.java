package com.misterblusky9.pocket;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class PymBoundaryTest {
    private static final Path SOURCES = Path.of("src", "main", "java");

    private static final Set<String> PYM_OWNED_TYPES = Set.of(
            "ScaleState", "ScaleController", "ScalePersistence", "ScaleSyncPayload", "ScaleRequestPayload",
            "ScaleHandshake", "SubLevelParentage", "JointScalePropagation", "ScaleTransitionCurve",
            "ColliderCompiler", "ColliderCoordinator", "ConstraintRefresh", "GenericConstraintState",
            "ScaledMassData", "ScaleFrame", "RapierBridge", "EntityScaleTracker", "InternalForceScaleContext",
            "CoupledMass", "ScaledBoundsCollider", "PlotShapeCache", "SubLevelConnections",
            "ScaleLimiter", "ResizeChecks", "MergedCouplingGraph", "UnsupportedScales", "PlotScan",
            "CompressionBlacklist", "PocketMetrics", "PocketPerformanceLimits", "PehkuiScaleBridge",
            "ScaleFormat", "ScaleReadout", "PocketClientFrame", "RenderFrame", "ColliderOutlineRenderer",
            "ScaledSurfaceNormal", "DisassemblyScaleAlignment", "SimulatedRopeScaleBoundary",
            "MergingGlueScaleGate", "PhysicsStaffScale", "ScaledContraptionCollider");

    private static final Map<Pattern, String> FORBIDDEN = Map.of(
            Pattern.compile("com\\.misterblusky9\\.pym\\.internal"),
            "Pocket must use Pym's public API, not its internals",
            Pattern.compile("(logical|render|last)Pose\\(\\)\\s*\\.scale\\(\\)\\s*\\.set\\("),
            "a live sublevel's pose scale is Pym's to write; request a resize instead",
            Pattern.compile("\"pym_[a-z_]+\"|\"pocket_parent\"|\"pocket_rivet_scale_initialized\""),
            "generic sublevel scale data is persisted by Pym",
            Pattern.compile("ScaleBounds\\.SUPPORTED"),
            "Pocket tools pass a Pocket tier, never Pym's supported band");

    private static final Map<Pattern, Set<String>> RESTRICTED = Map.of(
            Pattern.compile("virtuoel\\.pehkui"),
            Set.of(),
            Pattern.compile("dev\\.ryanhcode\\.sable\\.physics\\.impl\\.rapier"),
            Set.of());

    public static void main(final String[] args) {
        final List<String> failures = new ArrayList<>();
        final Path root = SOURCES.resolve(Path.of("com", "misterblusky9", "pocket"));
        if (!Files.isDirectory(root)) throw new AssertionError("run from the pocket project directory: " + root.toAbsolutePath());

        final int[] scanned = {0};
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                scanned[0]++;
                final String relative = root.relativize(path).toString().replace('\\', '/');
                final String name = path.getFileName().toString().replace(".java", "");
                final String source = read(path);

                if (PYM_OWNED_TYPES.contains(name)) {
                    failures.add(relative + ": recreates Pym-owned type " + name);
                }
                FORBIDDEN.forEach((pattern, reason) -> {
                    if (pattern.matcher(source).find()) failures.add(relative + ": " + reason);
                });
                RESTRICTED.forEach((pattern, allowed) -> {
                    if (!allowed.contains(relative) && pattern.matcher(source).find()) {
                        failures.add(relative + ": " + pattern + " is limited to " + allowed);
                    }
                });
            });
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }

        if (scanned[0] < 100) throw new AssertionError("suspiciously few sources scanned: " + scanned[0]);
        if (!failures.isEmpty()) throw new AssertionError("Pym boundary violations:\n  " + String.join("\n  ", failures));
        System.out.println("PymBoundaryTest: PASS (" + scanned[0] + " sources)");
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private PymBoundaryTest() {}
}
