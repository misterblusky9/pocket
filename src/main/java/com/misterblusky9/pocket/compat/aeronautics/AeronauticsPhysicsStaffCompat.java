package com.misterblusky9.pocket.compat.aeronautics;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class AeronauticsPhysicsStaffCompat {
    private static final String ITEM_ID = "physics_staff";
    private static final double DEFAULT_LINEAR_STIFFNESS = 2650.0D;
    private static final double DEFAULT_LINEAR_DAMPING = 125.0D;
    private static final double DEFAULT_ANGULAR_STIFFNESS = 10000.0D;
    private static final double DEFAULT_ANGULAR_DAMPING = 850.0D;

    public static boolean isHolding(final Player player) {
        if (player == null) return false;
        return isStaff(player.getMainHandItem()) || isStaff(player.getOffhandItem());
    }

    public static boolean isStaff(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        final ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null && ITEM_ID.equals(id.getPath())) return true;
        Class<?> type = stack.getItem().getClass();
        while (type != null) {
            if ("dev.simulated_team.simulated.content.physics_staff.PhysicsStaffItem".equals(type.getName())) return true;
            type = type.getSuperclass();
        }
        return false;
    }

    public static Tuning tuning() {
        try {
            final Class<?> serviceClass = Class.forName("dev.simulated_team.simulated.service.SimConfigService");
            final Field instanceField = serviceClass.getField("INSTANCE");
            final Object instance = instanceField.get(null);
            final Method serverMethod = serviceClass.getMethod("server");
            final Object server = serverMethod.invoke(instance);
            final Field physicsField = server.getClass().getField("physics");
            final Object physics = physicsField.get(server);
            return new Tuning(
                    configFloat(physics, "physicsStaffLinearStiffness", DEFAULT_LINEAR_STIFFNESS),
                    configFloat(physics, "physicsStaffLinearDamping", DEFAULT_LINEAR_DAMPING),
                    configFloat(physics, "physicsStaffAngularStiffness", DEFAULT_ANGULAR_STIFFNESS),
                    configFloat(physics, "physicsStaffAngularDamping", DEFAULT_ANGULAR_DAMPING)
            );
        } catch (final ReflectiveOperationException | RuntimeException ignored) {
            return defaults();
        }
    }

    private static double configFloat(final Object owner, final String name, final double fallback) {
        try {
            final Object config = owner.getClass().getField(name).get(owner);
            final Method getF = config.getClass().getMethod("getF");
            return ((Number) getF.invoke(config)).doubleValue();
        } catch (final ReflectiveOperationException | RuntimeException ignored) {
            return fallback;
        }
    }

    private static Tuning defaults() {
        return new Tuning(
                DEFAULT_LINEAR_STIFFNESS,
                DEFAULT_LINEAR_DAMPING,
                DEFAULT_ANGULAR_STIFFNESS,
                DEFAULT_ANGULAR_DAMPING
        );
    }

    public record Tuning(
            double linearStiffness,
            double linearDamping,
            double angularStiffness,
            double angularDamping
    ) {}

    private AeronauticsPhysicsStaffCompat() {}
}
