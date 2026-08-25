package com.misterblusky9.pocket.mixin.client;

import dev.engine_room.flywheel.api.backend.RenderContext;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.flywheel.SubLevelEmbedding;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.flywheel.BlockEntityStorageExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.client.PocketClientFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Vec3i;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Pseudo
@Mixin(
        targets = "dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl$RenderDispatcherImpl",
        remap = false,
        priority = 900
)
public abstract class FlywheelSubLevelScaleMixin {
    @Unique private static final Logger pocket$LOGGER = LogUtils.getLogger();
    @Unique private static volatile Field pocket$outerField;
    @Unique private static volatile Method pocket$blockEntitiesMethod;
    @Unique private static volatile Method pocket$renderOriginMethod;
    @Unique private static volatile Method pocket$getStorageMethod;

    @Unique private static volatile long pocket$retryAfterFrame;
    @Unique private static volatile int pocket$consecutiveFailures;
    @Unique private static final int pocket$MAX_FAILURES = 5;
    @Unique private static final long pocket$BACKOFF_FRAMES = 200L;

    @Inject(method = "onStartLevelRender", at = @At("HEAD"), order = 1100, remap = false)
    private void pocket$applyScaledSubLevelEmbeddings(
            final RenderContext context,
            final CallbackInfo ci
    ) {
        if (pocket$suspended()) return;

        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        final Object outer = this.pocket$getOuterManager();
        if (outer == null) return;

        final Object visualManager = pocket$invoke(
                pocket$getBlockEntitiesMethod(outer), outer);
        if (visualManager == null) return;

        final Object storageObject = pocket$invoke(
                pocket$getStorageMethod(visualManager), visualManager);
        if (!(storageObject instanceof final BlockEntityStorageExtension storage)) return;

        final Object renderOriginObject = pocket$invoke(
                pocket$getRenderOriginMethod(outer), outer);
        if (!(renderOriginObject instanceof final Vec3i parentOrigin)) return;

        for (final SubLevel rawSubLevel : container.getAllSubLevels()) {
            if (!(rawSubLevel instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) continue;

            final SubLevelEmbedding info = storage.sable$getEmbeddingInfo(subLevel);
            if (info == null) continue;

            final Pose3dc renderPose = subLevel.renderPose();
            final Vector3dc scale = renderPose.scale();
            if (Math.abs(scale.x() - 1.0D) <= PocketSized.EPSILON
                    && Math.abs(scale.y() - 1.0D) <= PocketSized.EPSILON
                    && Math.abs(scale.z() - 1.0D) <= PocketSized.EPSILON) continue;

            final VisualEmbedding embedding = info.embedding();
            final Vec3i localOrigin = embedding.renderOrigin();
            final Vector3dc position = renderPose.position();
            final Vector3d localOffset = renderPose.rotationPoint().sub(
                    localOrigin.getX(), localOrigin.getY(), localOrigin.getZ(), new Vector3d());

            final Matrix4f transform = new Matrix4f()
                    .translation(
                            (float) (position.x() - parentOrigin.getX()),
                            (float) (position.y() - parentOrigin.getY()),
                            (float) (position.z() - parentOrigin.getZ()))
                    .rotate(new Quaternionf(renderPose.orientation()))
                    .scale((float) scale.x(), (float) scale.y(), (float) scale.z())
                    .translate((float) -localOffset.x, (float) -localOffset.y, (float) -localOffset.z);

            final Matrix3f normal = transform.normal(new Matrix3f());
            embedding.transforms(transform, normal);
        }

        pocket$consecutiveFailures = 0;
    }

    @Unique
    private static boolean pocket$suspended() {
        if (pocket$consecutiveFailures >= pocket$MAX_FAILURES) return true;
        return PocketClientFrame.frame() < pocket$retryAfterFrame;
    }

    @Unique
    private Object pocket$getOuterManager() {
        try {
            Field field = pocket$outerField;
            if (field == null) {
                field = this.getClass().getDeclaredField("this$0");
                field.setAccessible(true);
                pocket$outerField = field;
            }
            return field.get(this);
        } catch (final ReflectiveOperationException | RuntimeException ex) {
            pocket$disableReflection(ex);
            return null;
        }
    }

    @Unique
    private static Method pocket$getBlockEntitiesMethod(final Object outer) {
        try {
            Method method = pocket$blockEntitiesMethod;
            if (method == null) {
                method = outer.getClass().getMethod("blockEntities");
                pocket$blockEntitiesMethod = method;
            }
            return method;
        } catch (final ReflectiveOperationException | RuntimeException ex) {
            pocket$disableReflection(ex);
            return null;
        }
    }

    @Unique
    private static Method pocket$getRenderOriginMethod(final Object outer) {
        try {
            Method method = pocket$renderOriginMethod;
            if (method == null) {
                method = outer.getClass().getMethod("renderOrigin");
                pocket$renderOriginMethod = method;
            }
            return method;
        } catch (final ReflectiveOperationException | RuntimeException ex) {
            pocket$disableReflection(ex);
            return null;
        }
    }

    @Unique
    private static Method pocket$getStorageMethod(final Object visualManager) {
        try {
            Method method = pocket$getStorageMethod;
            if (method == null) {
                method = visualManager.getClass().getMethod("getStorage");
                pocket$getStorageMethod = method;
            }
            return method;
        } catch (final ReflectiveOperationException | RuntimeException ex) {
            pocket$disableReflection(ex);
            return null;
        }
    }

    @Unique
    private static Object pocket$invoke(final Method method, final Object receiver) {
        if (method == null) return null;
        try {
            return method.invoke(receiver);
        } catch (final ReflectiveOperationException | RuntimeException ex) {
            pocket$disableReflection(ex);
            return null;
        }
    }

    @Unique
    private static void pocket$disableReflection(final Exception ex) {
        pocket$outerField = null;
        pocket$blockEntitiesMethod = null;
        pocket$renderOriginMethod = null;
        pocket$getStorageMethod = null;

        final int failures = ++pocket$consecutiveFailures;
        pocket$retryAfterFrame = PocketClientFrame.frame() + pocket$BACKOFF_FRAMES;

        if (failures == 1) {
            pocket$LOGGER.warn(
                    "Could not access Flywheel visualization manager; retrying shortly", ex);
        } else if (failures == pocket$MAX_FAILURES) {
            pocket$LOGGER.error(
                    "Flywheel visualization manager unreachable after {} attempts; scaled Create visuals disabled",
                    failures, ex);
        }
    }
}
