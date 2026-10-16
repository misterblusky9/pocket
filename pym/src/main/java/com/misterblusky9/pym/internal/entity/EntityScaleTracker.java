package com.misterblusky9.pym.internal.entity;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.compat.pehkui.PehkuiEntityScaling;
import com.misterblusky9.pym.internal.config.PymConfig;
import com.misterblusky9.pym.internal.extension.PymExtensions;
import com.misterblusky9.pym.internal.network.EntityScaleSyncPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class EntityScaleTracker {
    private static final String NBT_CURRENT = "pym_entity_scale";
    private static final String NBT_TARGET = "pym_entity_scale_target";
    private static final String NBT_REMAINING = "pym_entity_scale_ticks";

    private static final Map<Entity, NativeState> NATIVE =
            Collections.synchronizedMap(new WeakHashMap<>());

    public enum Backend {
        PEHKUI,
        NATIVE_STATIC,
        UNAVAILABLE
    }

    public static Backend backend(Entity entity) {
        if (entity == null || entity.isRemoved() || PymExtensions.exemptsEntity(entity)) {
            return Backend.UNAVAILABLE;
        }
        if (entity instanceof HangingEntity) return Backend.NATIVE_STATIC;
        if (PehkuiEntityScaling.present()) {
            return PehkuiEntityScaling.active() ? Backend.PEHKUI : Backend.UNAVAILABLE;
        }
        return nativeSupported(entity) ? Backend.NATIVE_STATIC : Backend.UNAVAILABLE;
    }

    public static boolean supports(Entity entity) {
        return backend(entity) != Backend.UNAVAILABLE;
    }

    public static double factor(Entity entity) {
        return switch (backend(entity)) {
            case PEHKUI -> PehkuiEntityScaling.scale(entity, false);
            case NATIVE_STATIC -> nativeState(entity, true).current;
            case UNAVAILABLE -> 1.0D;
        };
    }

    public static double target(Entity entity) {
        return switch (backend(entity)) {
            case PEHKUI -> PehkuiEntityScaling.scale(entity, true);
            case NATIVE_STATIC -> nativeState(entity, true).target;
            case UNAVAILABLE -> 1.0D;
        };
    }

    public static boolean resizing(Entity entity) {
        return switch (backend(entity)) {
            case PEHKUI -> PehkuiEntityScaling.changing(entity);
            case NATIVE_STATIC -> !ScaleBounds.same(factor(entity), target(entity));
            case UNAVAILABLE -> false;
        };
    }

    public static double base(Entity entity, boolean target) {
        return target ? target(entity) : factor(entity);
    }

    public static boolean set(Entity entity, double scale, int ticks) {
        if (!ScaleBounds.isValid(scale)) return false;
        return switch (backend(entity)) {
            case PEHKUI -> PehkuiEntityScaling.setScale(entity, scale, ticks);
            case NATIVE_STATIC -> {
                NativeState state = nativeState(entity, true);
                state.begin(nativeRepresentable(scale), ticks);
                persist(entity, state);
                entity.refreshDimensions();
                syncNative(entity, state);
                yield true;
            }
            case UNAVAILABLE -> false;
        };
    }

    public static boolean multiply(Entity entity, double ratio, int ticks) {
        if (!ScaleBounds.isValid(ratio)) return false;
        double goal = target(entity) * ratio;
        return ScaleBounds.isValid(goal) && set(entity, goal, ticks);
    }

    public static void tick(Entity entity) {
        if (entity == null || entity.isRemoved()) {
            if (entity != null) NATIVE.remove(entity);
            return;
        }
        if (!nativeOwned(entity)) return;

        NativeState state = nativeState(entity, false);
        if (state == null) return;

        double before = state.current;
        boolean arrived = state.step();
        if (!ScaleBounds.same(before, state.current)) {
            entity.refreshDimensions();
            if (!entity.level().isClientSide()) persist(entity, state);
        }

        if (!entity.level().isClientSide()
                && (arrived || entity.level().getGameTime() % 20L == 0L)) {
            syncNative(entity, state);
        }
    }

    public static double dimensionScale(Entity entity) {
        return backend(entity) == Backend.NATIVE_STATIC ? factor(entity) : 1.0D;
    }

    public static double renderScale(Entity entity, float partialTick) {
        return switch (backend(entity)) {
            case PEHKUI -> PehkuiEntityScaling.renderScale(entity, partialTick);
            case NATIVE_STATIC -> nativeState(entity, true).render(partialTick);
            case UNAVAILABLE -> 1.0D;
        };
    }

    public static double nativeRenderScale(Entity entity, float partialTick) {
        if (backend(entity) != Backend.NATIVE_STATIC) return 1.0D;
        return nativeState(entity, true).render(partialTick);
    }

    public static double nativePivotHeight(Entity entity) {
        return entity.isPassenger() ? entity.getEyeHeight() : 0.0D;
    }

    public static EntityDimensions applyScale(EntityDimensions base, double scale) {
        return base.scale((float) scale);
    }

    public static void acceptClientSnapshot(
            Entity entity,
            double current,
            double target,
            int remainingTicks
    ) {
        if (entity == null || !nativeOwned(entity)) return;
        NativeState state = NATIVE.computeIfAbsent(entity, ignored -> new NativeState(1.0D));
        state.snapshot(current, target, remainingTicks);
        entity.refreshDimensions();
    }

    private static boolean nativeOwned(Entity entity) {
        return entity instanceof HangingEntity || !PehkuiEntityScaling.present() && nativeSupported(entity);
    }

    public static boolean followsSublevel(Entity entity) {
        return PymConfig.resizeEntitiesWithSublevels() || entity.isPassenger() || nativeSupported(entity);
    }

    private static boolean nativeSupported(Entity entity) {
        return entity instanceof ArmorStand
                || entity instanceof HangingEntity
                || entity instanceof Display
                || PymExtensions.supportsNativeStaticEntity(entity);
    }

    private static NativeState nativeState(Entity entity, boolean create) {
        NativeState state = NATIVE.get(entity);
        if (state != null) return state;

        boolean persisted = !entity.level().isClientSide()
                && entity.getPersistentData().contains(NBT_CURRENT);
        if (!create && !persisted) return null;

        double current = 1.0D;
        double target = 1.0D;
        int remaining = 0;
        if (!entity.level().isClientSide()) {
            CompoundTag tag = entity.getPersistentData();
            if (tag.contains(NBT_CURRENT)) current = sanitize(tag.getDouble(NBT_CURRENT));
            if (tag.contains(NBT_TARGET)) target = sanitize(tag.getDouble(NBT_TARGET));
            else target = current;
            if (tag.contains(NBT_REMAINING)) remaining = Math.max(0, tag.getInt(NBT_REMAINING));
        }

        state = new NativeState(current);
        state.snapshot(current, target, remaining);
        NATIVE.put(entity, state);
        return state;
    }

    private static void persist(Entity entity, NativeState state) {
        if (entity == null || entity.level().isClientSide()) return;
        CompoundTag tag = entity.getPersistentData();
        if (state.neutral()) {
            tag.remove(NBT_CURRENT);
            tag.remove(NBT_TARGET);
            tag.remove(NBT_REMAINING);
            return;
        }
        tag.putDouble(NBT_CURRENT, state.current);
        tag.putDouble(NBT_TARGET, state.target);
        tag.putInt(NBT_REMAINING, state.remaining());
    }

    private static void syncNative(Entity entity, NativeState state) {
        if (entity == null || entity.level().isClientSide()) return;
        PacketDistributor.sendToPlayersInDimension(
                (ServerLevel) entity.level(),
                new EntityScaleSyncPayload(entity.getId(), state.current, state.target, state.remaining())
        );
    }

    private static double nativeRepresentable(double scale) {
        if (scale >= Float.MAX_VALUE) return Float.MAX_VALUE;
        if (scale <= Float.MIN_VALUE) return Float.MIN_VALUE;
        return scale;
    }

    private static double sanitize(double scale) {
        return ScaleBounds.isValid(scale) ? nativeRepresentable(scale) : 1.0D;
    }

    private static final class NativeState {
        private double previous;
        private double start;
        private double current;
        private double target;
        private int elapsed;
        private int duration;

        private NativeState(double scale) {
            this.previous = scale;
            this.start = scale;
            this.current = scale;
            this.target = scale;
        }

        private void begin(double goal, int ticks) {
            this.previous = this.current;
            if (ticks <= 0) {
                this.start = goal;
                this.current = goal;
                this.target = goal;
                this.elapsed = 0;
                this.duration = 0;
                return;
            }
            this.start = this.current;
            this.target = goal;
            this.elapsed = 0;
            this.duration = Math.max(1, ticks);
        }

        private void snapshot(double current, double target, int remaining) {
            this.previous = sanitize(current);
            this.current = sanitize(current);
            this.start = this.current;
            this.target = sanitize(target);
            this.elapsed = 0;
            this.duration = Math.max(0, remaining);
            if (this.duration == 0) this.current = this.target;
        }

        private boolean step() {
            this.previous = this.current;
            if (this.duration <= 0 || ScaleBounds.same(this.current, this.target)) return false;
            this.elapsed++;
            if (this.elapsed >= this.duration) {
                this.current = this.target;
                this.start = this.target;
                this.elapsed = 0;
                this.duration = 0;
                return true;
            }
            double t = this.elapsed / (double) this.duration;
            double eased = 1.0D - (1.0D - t) * (1.0D - t);
            this.current = this.start + (this.target - this.start) * eased;
            return false;
        }

        private double render(float partialTick) {
            if (!Float.isFinite(partialTick)) return this.current;
            double t = Math.max(0.0D, Math.min(1.0D, partialTick));
            return this.previous + (this.current - this.previous) * t;
        }

        private int remaining() {
            return Math.max(0, this.duration - this.elapsed);
        }

        private boolean neutral() {
            return ScaleBounds.same(this.current, 1.0D)
                    && ScaleBounds.same(this.target, 1.0D)
                    && this.duration == 0;
        }
    }

    private EntityScaleTracker() {
    }
}
