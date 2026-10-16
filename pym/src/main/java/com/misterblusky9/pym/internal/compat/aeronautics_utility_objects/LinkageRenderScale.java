package com.misterblusky9.pym.internal.compat.aeronautics_utility_objects;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;

public final class LinkageRenderScale {
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

    public static float partnerRatio(BlockEntity owner) {
        try {
            Method getter = PARTNER_GETTER.get(owner.getClass());
            if (getter == null || !(getter.invoke(owner) instanceof BlockEntity partner)) return 1.0F;
            SubLevel first = Sable.HELPER.getContaining(owner);
            SubLevel second = Sable.HELPER.getContaining(partner);
            double s1 = first instanceof ClientSubLevel client ? client.renderPose().scale().x() : 1.0D;
            double s2 = second instanceof ClientSubLevel client ? client.renderPose().scale().x() : 1.0D;
            if (!(s1 > 0.0D) || !(s2 > 0.0D) || !Double.isFinite(s1) || !Double.isFinite(s2)) {
                return 1.0F;
            }
            return (float) (s2 / s1);
        } catch (ReflectiveOperationException exception) {
            return 1.0F;
        }
    }

    private LinkageRenderScale() {}
}
