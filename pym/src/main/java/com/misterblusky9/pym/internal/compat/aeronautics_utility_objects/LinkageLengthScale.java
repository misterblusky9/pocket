package com.misterblusky9.pym.internal.compat.aeronautics_utility_objects;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.scale.ScaleState;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.Position;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.lang.reflect.Method;

public final class LinkageLengthScale {
    private static final ThreadLocal<Double> IMPULSE_SCALE = new ThreadLocal<>();

    private static final ClassValue<Method> PARTNER_GETTER = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                return type.getMethod(type.getSimpleName().startsWith("UniversalJoint")
                        ? "getLoadedLinkedJoint" : "getLoadedLinkedConnectionHead");
            } catch (ReflectiveOperationException exception) {
                return null;
            }
        }
    };

    @Nullable
    public static BlockEntity partner(BlockEntity owner) {
        try {
            Method getter = PARTNER_GETTER.get(owner.getClass());
            return getter != null && getter.invoke(owner) instanceof BlockEntity partner ? partner : null;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    public static double ofLink(BlockEntity owner) {
        BlockEntity partner = partner(owner);
        if (partner == null) return 1.0D;
        return between(Sable.HELPER.getContaining(owner), Sable.HELPER.getContaining(partner));
    }

    public static double between(Level level, Position first, Position second) {
        return between(Sable.HELPER.getContaining(level, first), Sable.HELPER.getContaining(level, second));
    }

    public static double between(@Nullable SubLevel first, @Nullable SubLevel second) {
        double s1 = ScaleState.getScale(first);
        double s2 = ScaleState.getScale(second);
        if (!ScaleBounds.isValid(s1) || !ScaleBounds.isValid(s2) || !ScaleBounds.same(s1, s2)) return 1.0D;
        return s1;
    }

    public static void enterImpulseScale(double scale) {
        IMPULSE_SCALE.set(scale);
    }

    public static void exitImpulseScale() {
        IMPULSE_SCALE.remove();
    }

    public static double activeImpulseScale() {
        Double scale = IMPULSE_SCALE.get();
        return scale == null || !(scale > 0.0D) ? 1.0D : scale;
    }

    public static Vector3d toNominal(Vector3d world, double scale) {
        return scale == 1.0D ? world : world.div(scale);
    }

    private LinkageLengthScale() {}
}
