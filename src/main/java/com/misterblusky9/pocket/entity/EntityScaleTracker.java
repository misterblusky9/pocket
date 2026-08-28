package com.misterblusky9.pocket.entity;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class EntityScaleTracker {
    private static final int LOST_TRACKING_GRACE_TICKS = 10;

    private static final double NEAR_HORIZONTAL_MARGIN = 2.00D;
    private static final double NEAR_VERTICAL_MARGIN = 3.00D;

    private static final double MIN_NEAR_MARGIN = 0.25D;
    private static final int SUBLEVEL_SWITCH_CONFIRM_TICKS = 3;

    private static final Map<Entity, State> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static void tick(final Entity entity) {
        if (entity == null) return;

        if (entity.isRemoved()) {
            STATES.remove(entity);
            if (PehkuiScaleBridge.ownsScaling()) {
                PehkuiScaleBridge.clear(entity);
            }
            return;
        }

        if (entity instanceof final Player player) {
            if (PehkuiScaleBridge.ownsScaling()) {
                tickPehkuiPlayer(player);
            } else {
                tickPlayer(player);
            }
            return;
        }

        if (entity instanceof Projectile) {
            STATES.remove(entity);
            if (PehkuiScaleBridge.ownsScaling()) {
                PehkuiScaleBridge.clear(entity);
            }
            return;
        }

        if (!STATES.containsKey(entity) && !hasSubLevels(entity)) return;

        final Association immediate = resolveImmediate(entity);
        final State state = STATES.computeIfAbsent(entity, ignored -> new State());

        final double before = state.dimensionScale;

        if (immediate.mode == Mode.CONTAINED) {
            state.mode = Mode.CONTAINED;
            state.subLevel = immediate.subLevel;
            state.missingTicks = 0;
            clearCandidate(state);
            state.dimensionScale = 1.0D;
        } else if (immediate.mode == Mode.TRACKING) {
            if (state.mode == Mode.TRACKING
                    && state.subLevel != null
                    && state.subLevel != immediate.subLevel
                    && !state.subLevel.isRemoved()
                    && shouldKeepPreviousBinding(entity, state)) {
                if (state.candidateSubLevel == immediate.subLevel) {
                    state.candidateTicks++;
                } else {
                    state.candidateSubLevel = immediate.subLevel;
                    state.candidateTicks = 1;
                }

                if (state.candidateTicks >= SUBLEVEL_SWITCH_CONFIRM_TICKS) {
                    state.subLevel = immediate.subLevel;
                    state.missingTicks = 0;
                    state.dimensionScale = scaleOf(immediate.subLevel);
                    clearCandidate(state);
                } else {
                    state.dimensionScale = scaleOf(state.subLevel);
                }
            } else {
                state.mode = Mode.TRACKING;
                state.subLevel = immediate.subLevel;
                state.missingTicks = 0;
                state.dimensionScale = scaleOf(immediate.subLevel);
                clearCandidate(state);
            }
        } else if (state.mode == Mode.TRACKING && shouldKeepPreviousBinding(entity, state)) {
            clearCandidate(state);
            state.dimensionScale = scaleOf(state.subLevel);
        } else {
            if (state.mode == Mode.TRACKING && state.missingTicks < LOST_TRACKING_GRACE_TICKS) {
                state.missingTicks++;
                clearCandidate(state);
                state.dimensionScale = scaleOf(state.subLevel);
            } else {
                state.mode = Mode.NONE;
                state.subLevel = null;
                state.missingTicks = 0;
                clearCandidate(state);
                state.dimensionScale = 1.0D;
            }
        }

        state.dimensionScale = sanitize(state.dimensionScale);

        if (PehkuiScaleBridge.ownsScaling()) {
            syncPehkui(entity, state);
        } else if (Math.abs(before - state.dimensionScale) > 1.0E-5D) {
            entity.refreshDimensions();
        }

        if (state.mode == Mode.NONE
                && Math.abs(state.dimensionScale - 1.0D) <= PocketSized.EPSILON) {
            STATES.remove(entity);
        }
    }

    public static double dimensionScale(final Entity entity) {
        if (PehkuiScaleBridge.ownsScaling()) return 1.0D;

        final State state = STATES.get(entity);
        if (state == null) return 1.0D;
        return sanitize(state.dimensionScale);
    }

    public static EntityDimensions applyScale(
            final Entity entity,
            final EntityDimensions base,
            final double scale
    ) {
        final float factor = (float) scale;
        if (!(entity instanceof Player)) return base.scale(factor);

        return new EntityDimensions(
                base.width() * factor,
                base.height() * factor,
                base.eyeHeight(),
                base.attachments(),
                base.fixed());
    }

    private static void tickPlayer(final Player player) {
        final SubLevel ridden = riddenSubLevel(player);

        if (ridden == null) {
            final State previous = STATES.remove(player);

            if (previous != null && Math.abs(previous.dimensionScale - 1.0D) > 1.0E-5D) {
                player.refreshDimensions();
            }
            return;
        }

        final State state = STATES.computeIfAbsent(player, ignored -> new State());
        final double before = state.dimensionScale;

        state.mode = Mode.TRACKING;
        state.subLevel = ridden;
        state.missingTicks = 0;
        clearCandidate(state);
        state.dimensionScale = sanitize(scaleOf(ridden));

        if (Math.abs(before - state.dimensionScale) > 1.0E-5D) {
            player.refreshDimensions();
        }
    }

    private static void tickPehkuiPlayer(final Player player) {
        final SubLevel ridden = riddenSubLevel(player);
        State state = STATES.get(player);

        if (ridden != null) {
            if (state == null) {
                state = new State();
                STATES.put(player, state);
            }

            final double scale = sanitize(scaleOf(ridden));
            final boolean changed = !state.seatScaled
                    || state.subLevel != ridden
                    || Math.abs(state.dimensionScale - scale) > PocketSized.EPSILON;

            state.mode = Mode.TRACKING;
            state.subLevel = ridden;
            state.missingTicks = 0;
            state.dimensionScale = scale;
            state.seatScaled = true;
            clearCandidate(state);

            if (changed) {
                PehkuiScaleBridge.setPersonalScale(player, scale);
            }
            return;
        }

        if (state == null || !state.seatScaled) {
            STATES.remove(player);
            return;
        }

        if (state.subLevel == null || state.subLevel.isRemoved()) {
            PehkuiScaleBridge.clearPersonalScale(player);
            STATES.remove(player);
            return;
        }

        final Association immediate = resolveImmediate(player);
        if (immediate.subLevel == state.subLevel || shouldKeepPreviousBinding(player, state)) {
            state.mode = Mode.TRACKING;
            state.missingTicks = 0;
            return;
        }

        state.missingTicks++;
        if (state.missingTicks <= LOST_TRACKING_GRACE_TICKS) return;

        PehkuiScaleBridge.clearPersonalScale(player);
        STATES.remove(player);
    }

    public static double pehkuiRidingScale(final Entity entity, final SubLevel subLevel) {
        if (!(entity instanceof final Player player) || !PehkuiScaleBridge.ownsScaling()) return 1.0D;
        final SubLevel ridden = riddenSubLevel(player);
        if (ridden == null || ridden != subLevel || ridden.isRemoved()) return 1.0D;
        return sanitize(scaleOf(ridden));
    }

    private static SubLevel riddenSubLevel(final Player player) {
        final Set<Entity> visited = new HashSet<>();
        Entity vehicle = player.getVehicle();

        while (vehicle != null && visited.add(vehicle)) {
            SubLevel subLevel = Sable.HELPER.getContaining(vehicle);
            if (subLevel != null && !subLevel.isRemoved()) return subLevel;

            subLevel = Sable.HELPER.getTrackingSubLevel(vehicle);
            if (subLevel != null && !subLevel.isRemoved()) return subLevel;

            vehicle = vehicle.getVehicle();
        }

        return null;
    }

    public static double renderScale(final Entity entity, final float partialTick) {
        if (PehkuiScaleBridge.ownsScaling()) return 1.0D;

        if (entity instanceof final Player player) {
            final SubLevel ridden = riddenSubLevel(player);
            if (ridden == null || ridden.isRemoved()) return 1.0D;
            if (ridden instanceof final ClientSubLevel clientSubLevel) {
                return sanitize(clientSubLevel.renderPose(partialTick).scale().x());
            }
            return scaleOf(ridden);
        }

        final SubLevel containing = Sable.HELPER.getContaining(entity);
        if (containing != null && !containing.isRemoved()) {
            if (containing instanceof final ClientSubLevel clientSubLevel) {
                return sanitize(clientSubLevel.renderPose(partialTick).scale().x());
            }
            return scaleOf(containing);
        }

        final State state = STATES.get(entity);
        if (state == null) {
            final Association immediate = resolveImmediate(entity);
            if (immediate.mode == Mode.TRACKING && immediate.subLevel != null) {
                if (immediate.subLevel instanceof final ClientSubLevel clientSubLevel) {
                    return sanitize(clientSubLevel.renderPose(partialTick).scale().x());
                }
                return scaleOf(immediate.subLevel);
            }
            return 1.0D;
        }

        if (state.mode == Mode.TRACKING
                && state.subLevel instanceof final ClientSubLevel clientSubLevel
                && !clientSubLevel.isRemoved()) {
            return sanitize(clientSubLevel.renderPose(partialTick).scale().x());
        }

        return sanitize(state.dimensionScale);
    }

    public static boolean isContained(final Entity entity) {
        final SubLevel containing = Sable.HELPER.getContaining(entity);
        return containing != null && !containing.isRemoved();
    }

    private static void syncPehkui(final Entity entity, final State state) {
        double inheritedBaseScale = 1.0D;
        double containedModelScale = 1.0D;

        if (state.mode == Mode.TRACKING) {
            inheritedBaseScale = sanitize(state.dimensionScale);
        } else if (state.mode == Mode.CONTAINED
                && state.subLevel != null
                && !state.subLevel.isRemoved()) {
            containedModelScale = scaleOf(state.subLevel);
        }

        PehkuiScaleBridge.apply(
                entity,
                inheritedBaseScale,
                containedModelScale
        );
    }

    private static boolean shouldKeepPreviousBinding(final Entity entity, final State state) {
        if (state.subLevel == null || state.subLevel.isRemoved()) return false;

        final Vec3 p = entity.position();
        final var bounds = state.subLevel.boundingBox();

        final double scale = scaleOf(state.subLevel);
        final double horizontal = Math.max(MIN_NEAR_MARGIN, NEAR_HORIZONTAL_MARGIN * scale);
        final double vertical = Math.max(MIN_NEAR_MARGIN, NEAR_VERTICAL_MARGIN * scale);

        final boolean near =
                p.x >= bounds.minX() - horizontal
                        && p.x <= bounds.maxX() + horizontal
                        && p.z >= bounds.minZ() - horizontal
                        && p.z <= bounds.maxZ() + horizontal
                        && p.y >= bounds.minY() - vertical
                        && p.y <= bounds.maxY() + vertical;

        if (near) {
            state.missingTicks = 0;
            return true;
        }

        return false;
    }

    private static boolean hasSubLevels(final Entity entity) {
        final var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer
                .getContainer(entity.level());
        return container != null && !container.getAllSubLevels().isEmpty();
    }

    private static Association resolveImmediate(final Entity entity) {
        SubLevel subLevel = Sable.HELPER.getContaining(entity);
        if (subLevel != null && !subLevel.isRemoved()) {
            return new Association(Mode.CONTAINED, subLevel);
        }

        subLevel = Sable.HELPER.getTrackingSubLevel(entity);
        if (subLevel != null && !subLevel.isRemoved()) {
            return new Association(Mode.TRACKING, subLevel);
        }

        final Set<Entity> visited = new HashSet<>();
        Entity vehicle = entity.getVehicle();

        while (vehicle != null && visited.add(vehicle)) {
            subLevel = Sable.HELPER.getContaining(vehicle);
            if (subLevel != null && !subLevel.isRemoved()) {
                return new Association(Mode.TRACKING, subLevel);
            }

            subLevel = Sable.HELPER.getTrackingSubLevel(vehicle);
            if (subLevel != null && !subLevel.isRemoved()) {
                return new Association(Mode.TRACKING, subLevel);
            }

            vehicle = vehicle.getVehicle();
        }

        return new Association(Mode.NONE, null);
    }

    private static double scaleOf(final SubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved()) return 1.0D;

        if (subLevel instanceof final ServerSubLevel serverSubLevel) {
            return sanitize(ScaleState.getServerScale(serverSubLevel));
        }

        if (subLevel instanceof final ClientSubLevel clientSubLevel) {
            final double networkScale = ScaleState.getClientScale(clientSubLevel);
            final double logicalScale = clientSubLevel.logicalPose().scale().x();

            if (Math.abs(networkScale - 1.0D) > PocketSized.EPSILON) {
                return sanitize(networkScale);
            }

            return sanitize(logicalScale);
        }

        return sanitize(subLevel.logicalPose().scale().x());
    }

    private static void clearCandidate(final State state) {
        state.candidateSubLevel = null;
        state.candidateTicks = 0;
    }

    private static double sanitize(final double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0D) return 1.0D;
        return Math.max(PocketSized.MIN_SCALE, Math.min(1.0D, scale));
    }

    private enum Mode {
        NONE,
        CONTAINED,
        TRACKING
    }

    private record Association(Mode mode, SubLevel subLevel) {
    }

    private static final class State {
        private Mode mode = Mode.NONE;
        private SubLevel subLevel;
        private int missingTicks;
        private SubLevel candidateSubLevel;
        private int candidateTicks;
        private double dimensionScale = 1.0D;
        private boolean seatScaled;
    }

    private EntityScaleTracker() {
    }
}
