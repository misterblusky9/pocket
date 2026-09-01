package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compat.create.ContraptionCollisionScaleContext;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionCollider;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.foundation.collision.CollisionList;
import com.simibubi.create.foundation.collision.ContinuousOBBCollider;
import com.simibubi.create.foundation.collision.OrientedBB;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = ContraptionCollider.class, remap = false)
public abstract class ContraptionColliderScaleMixin {
    @Unique
    private static final ThreadLocal<CollisionList> pocket$SCALED_COLLIDERS =
            ThreadLocal.withInitial(CollisionList::new);

    @ModifyArg(
            method = "collideEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/contraptions/ContraptionCollider;"
                            + "getPotentiallyCollidedShapes(Lnet/minecraft/world/level/Level;"
                            + "Lcom/simibubi/create/content/contraptions/Contraption;"
                            + "Lnet/minecraft/world/phys/AABB;"
                            + "Lnet/minecraft/world/phys/shapes/Shapes$DoubleLineConsumer;)V"
            ),
            index = 2,
            require = 1
    )
    private static AABB pocket$scanFallbackInCanonicalLocalSpace(
            final AABB localBounds,
            @Local(argsOnly = true) final AbstractContraptionEntity contraptionEntity
    ) {
        final double scale = pocket$scaleOf(contraptionEntity);
        if (Math.abs(scale - 1.0D) <= PocketSized.EPSILON) return localBounds;
        return pocket$scaleAabbAboutCreateOrigin(localBounds, 1.0D / scale);
    }

    @WrapOperation(
            method = "collideEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/collision/ContinuousOBBCollider;"
                            + "collideMany(Lcom/simibubi/create/foundation/collision/CollisionList;"
                            + "Lcom/simibubi/create/foundation/collision/CollisionList;"
                            + "Lcom/simibubi/create/foundation/collision/OrientedBB;"
                            + "Lnet/minecraft/world/phys/Vec3;FZ)"
                            + "Lcom/simibubi/create/foundation/collision/ContinuousOBBCollider$CollisionResponse;"
            ),
            require = 1
    )
    private static ContinuousOBBCollider.CollisionResponse pocket$solveAtWorldScale(
            final CollisionList collidableBBs,
            final CollisionList denseViableColliders,
            final OrientedBB obb,
            final Vec3 motion,
            final float entityMaxStep,
            final boolean doHorizontalPass,
            final Operation<ContinuousOBBCollider.CollisionResponse> original,
            @Local(argsOnly = true) final AbstractContraptionEntity contraptionEntity
    ) {
        final double scale = pocket$scaleOf(contraptionEntity);
        if (Math.abs(scale - 1.0D) <= PocketSized.EPSILON || collidableBBs.size == 0) {
            return original.call(collidableBBs, denseViableColliders, obb, motion, entityMaxStep, doHorizontalPass);
        }

        final CollisionList scaledColliders = pocket$scaledCopy(collidableBBs, scale);
        final double previousScale = ContraptionCollisionScaleContext.swap(scale);
        final ContinuousOBBCollider.CollisionResponse response;
        try {
            response = original.call(
                    scaledColliders,
                    denseViableColliders,
                    obb,
                    motion,
                    (float) (entityMaxStep * scale),
                    doHorizontalPass
            );
        } finally {
            ContraptionCollisionScaleContext.restore(previousScale);
        }

        if (contraptionEntity instanceof CarriageContraptionEntity && response.surfaceCollision) {
            final double upwardResponse = response.collisionResponse.y;
            final double upwardNormal = response.normal.y;
            if (upwardResponse <= 1.0E-9D && upwardNormal <= 1.0E-9D) {
                response.surfaceCollision = false;
            }
        }

        return response;
    }

    @ModifyConstant(
            method = "collideEntities",
            constant = @Constant(floatValue = 0.0078125F),
            require = 1
    )
    private static float pocket$scaleHorizontalResponseEpsilon(
            final float original,
            @Local(argsOnly = true) final AbstractContraptionEntity contraptionEntity
    ) {
        return (float) (original * pocket$scaleOf(contraptionEntity));
    }

    @Unique
    private static double pocket$scaleOf(final AbstractContraptionEntity contraptionEntity) {
        final SubLevel subLevel = Sable.HELPER.getContaining(contraptionEntity);
        if (subLevel == null || subLevel.isRemoved()) return 1.0D;

        final Vector3dc scale = subLevel.logicalPose().scale();
        if (!PocketSized.isValidScale(scale.x())
                || Math.abs(scale.x() - scale.y()) > PocketSized.EPSILON
                || Math.abs(scale.x() - scale.z()) > PocketSized.EPSILON) {
            return 1.0D;
        }
        return scale.x();
    }

    @Unique
    private static AABB pocket$scaleAabbAboutCreateOrigin(final AABB box, final double scale) {
        return new AABB(
                0.5D + (box.minX - 0.5D) * scale,
                0.5D + (box.minY - 0.5D) * scale,
                0.5D + (box.minZ - 0.5D) * scale,
                0.5D + (box.maxX - 0.5D) * scale,
                0.5D + (box.maxY - 0.5D) * scale,
                0.5D + (box.maxZ - 0.5D) * scale
        );
    }

    @Unique
    private static CollisionList pocket$scaledCopy(final CollisionList source, final double scale) {
        final CollisionList target = pocket$SCALED_COLLIDERS.get();
        pocket$ensureCapacity(target, source.size);
        target.size = source.size;

        for (int i = 0; i < source.size; i++) {
            target.centerX[i] = 0.5D + (source.centerX[i] - 0.5D) * scale;
            target.centerY[i] = 0.5D + (source.centerY[i] - 0.5D) * scale;
            target.centerZ[i] = 0.5D + (source.centerZ[i] - 0.5D) * scale;
            target.extentsX[i] = source.extentsX[i] * scale;
            target.extentsY[i] = source.extentsY[i] * scale;
            target.extentsZ[i] = source.extentsZ[i] * scale;
        }
        return target;
    }

    @Unique
    private static void pocket$ensureCapacity(final CollisionList list, final int required) {
        if (list.centerX.length >= required) return;

        int capacity = Math.max(CollisionList.DEFAULT_CAPACITY, list.centerX.length);
        while (capacity < required) capacity <<= 1;

        list.centerX = new double[capacity];
        list.centerY = new double[capacity];
        list.centerZ = new double[capacity];
        list.extentsX = new double[capacity];
        list.extentsY = new double[capacity];
        list.extentsZ = new double[capacity];
    }
}
