package com.misterblusky9.pym.internal.compat.create.mixin;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.client.RenderFrame;
import com.mojang.logging.LogUtils;
import dev.engine_room.flywheel.api.backend.RenderContext;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.flywheel.SubLevelEmbedding;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.flywheel.BlockEntityStorageExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Vec3i;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Pseudo
@Mixin(
        targets = "dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl$RenderDispatcherImpl",
        remap = false,
        priority = 900
)
public abstract class FlywheelSubLevelScaleMixin {
    @Unique private static final Logger pym$LOGGER = LogUtils.getLogger();
    @Unique private static volatile Field pym$outerField;
    @Unique private static volatile Method pym$blockEntitiesMethod;
    @Unique private static volatile Method pym$renderOriginMethod;
    @Unique private static volatile Method pym$getStorageMethod;
    @Unique private static volatile long pym$retryAfterFrame;
    @Unique private static volatile int pym$consecutiveFailures;
    @Unique private static final int pym$MAX_FAILURES = 5;
    @Unique private static final long pym$BACKOFF_FRAMES = 200L;

    @Inject(method = "onStartLevelRender", at = @At("HEAD"), order = 1100, remap = false)
    private void pym$applyScaledSubLevelEmbeddings(
            RenderContext context,
            CallbackInfo ci
    ) {
        if (pym$suspended()) return;

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        Object outer = this.pym$getOuterManager();
        if (outer == null) return;

        Object visualManager = pym$invoke(pym$getBlockEntitiesMethod(outer), outer);
        if (visualManager == null) return;

        Object storageObject = pym$invoke(pym$getStorageMethod(visualManager), visualManager);
        if (!(storageObject instanceof BlockEntityStorageExtension storage)) return;

        Object renderOriginObject = pym$invoke(pym$getRenderOriginMethod(outer), outer);
        if (!(renderOriginObject instanceof Vec3i parentOrigin)) return;

        for (SubLevel rawSubLevel : container.getAllSubLevels()) {
            if (!(rawSubLevel instanceof ClientSubLevel subLevel) || subLevel.isRemoved()) continue;

            SubLevelEmbedding info = storage.sable$getEmbeddingInfo(subLevel);
            if (info == null) continue;

            Pose3dc renderPose = subLevel.renderPose();
            Vector3dc scale = renderPose.scale();
            if (Math.abs(scale.x() - 1.0D) <= ScaleBounds.EPSILON
                    && Math.abs(scale.y() - 1.0D) <= ScaleBounds.EPSILON
                    && Math.abs(scale.z() - 1.0D) <= ScaleBounds.EPSILON) continue;

            VisualEmbedding embedding = info.embedding();
            Vec3i localOrigin = embedding.renderOrigin();
            Vector3dc position = renderPose.position();
            Vector3d translation = renderPose.rotationPoint().sub(
                    localOrigin.getX(), localOrigin.getY(), localOrigin.getZ(), new Vector3d());

            translation.mul(-scale.x(), -scale.y(), -scale.z());
            renderPose.orientation().transform(translation);
            translation.add(
                    position.x() - parentOrigin.getX(),
                    position.y() - parentOrigin.getY(),
                    position.z() - parentOrigin.getZ());

            Matrix4f transform = new Matrix4f()
                    .rotation(new Quaternionf(renderPose.orientation()))
                    .scale((float) scale.x(), (float) scale.y(), (float) scale.z())
                    .setTranslation((float) translation.x, (float) translation.y, (float) translation.z);

            Matrix3f normal = transform.normal(new Matrix3f());
            embedding.transforms(transform, normal);
        }

        pym$consecutiveFailures = 0;
    }

    @Unique
    private static boolean pym$suspended() {
        if (pym$consecutiveFailures >= pym$MAX_FAILURES) return true;
        return RenderFrame.frame() < pym$retryAfterFrame;
    }

    @Unique
    private Object pym$getOuterManager() {
        try {
            Field field = pym$outerField;
            if (field == null) {
                field = this.getClass().getDeclaredField("this$0");
                field.setAccessible(true);
                pym$outerField = field;
            }
            return field.get(this);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            pym$disableReflection(ex);
            return null;
        }
    }

    @Unique
    private static Method pym$getBlockEntitiesMethod(Object outer) {
        try {
            Method method = pym$blockEntitiesMethod;
            if (method == null) {
                method = outer.getClass().getMethod("blockEntities");
                pym$blockEntitiesMethod = method;
            }
            return method;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            pym$disableReflection(ex);
            return null;
        }
    }

    @Unique
    private static Method pym$getRenderOriginMethod(Object outer) {
        try {
            Method method = pym$renderOriginMethod;
            if (method == null) {
                method = outer.getClass().getMethod("renderOrigin");
                pym$renderOriginMethod = method;
            }
            return method;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            pym$disableReflection(ex);
            return null;
        }
    }

    @Unique
    private static Method pym$getStorageMethod(Object visualManager) {
        try {
            Method method = pym$getStorageMethod;
            if (method == null) {
                method = visualManager.getClass().getMethod("getStorage");
                pym$getStorageMethod = method;
            }
            return method;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            pym$disableReflection(ex);
            return null;
        }
    }

    @Unique
    private static Object pym$invoke(Method method, Object receiver) {
        if (method == null) return null;
        try {
            return method.invoke(receiver);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            pym$disableReflection(ex);
            return null;
        }
    }

    @Unique
    private static void pym$disableReflection(Exception ex) {
        pym$outerField = null;
        pym$blockEntitiesMethod = null;
        pym$renderOriginMethod = null;
        pym$getStorageMethod = null;

        int failures = ++pym$consecutiveFailures;
        pym$retryAfterFrame = RenderFrame.frame() + pym$BACKOFF_FRAMES;

        if (failures == 1) {
            pym$LOGGER.warn("Could not access Flywheel visualization manager; retrying shortly", ex);
        } else if (failures == pym$MAX_FAILURES) {
            pym$LOGGER.error(
                    "Flywheel visualization manager unreachable after {} attempts; scaled Create visuals disabled",
                    failures, ex);
        }
    }
}
