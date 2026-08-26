package com.misterblusky9.pocket.physics;

import com.misterblusky9.pocket.debug.PocketTrace;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

import java.lang.reflect.Method;

public final class RapierBridge {
    private static final Class<?> RAPIER_3D;
    private static final Method CREATE_VOXEL_COLLIDER_ENTRY;
    private static final Method REMOVE_CONSTRAINT;
    private static final Method ADD_CHUNK;
    private static final Method REMOVE_CHUNK;
    private static final Method CHANGE_BLOCK;
    private static final Method GET_SCENE_HANDLE;
    private static final Method GET_ID;
    private static final Method SET_LOCAL_BOUNDS;
    private static final Method COLLIDER_HANDLE;
    private static final Method COLLIDER_ADD_BOX;
    private static final java.lang.reflect.Constructor<?> COLLIDER_OF_HANDLE;
    private static final Method COLLIDER_CLEAR_BOXES;

    static {
        try {
            RAPIER_3D = Class.forName("dev.ryanhcode.sable.physics.impl.rapier.Rapier3D");
            CREATE_VOXEL_COLLIDER_ENTRY = find(RAPIER_3D, "createVoxelColliderEntry", 5);
            REMOVE_CONSTRAINT = RAPIER_3D.getMethod("removeConstraint", long.class, long.class);
            ADD_CHUNK = find(RAPIER_3D, "addChunk", 7);
            REMOVE_CHUNK = find(RAPIER_3D, "removeChunk", 5);
            CHANGE_BLOCK = find(RAPIER_3D, "changeBlock", 5);
            GET_SCENE_HANDLE = RAPIER_3D.getMethod("getSceneHandle", ServerLevel.class);
            GET_ID = RAPIER_3D.getMethod("getID", PhysicsPipelineBody.class);
            SET_LOCAL_BOUNDS = find(RAPIER_3D, "setLocalBounds", 8);

            final Class<?> colliderClass = Class.forName("dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderData");
            COLLIDER_HANDLE = colliderClass.getMethod("handle");
            COLLIDER_ADD_BOX = find(colliderClass, "addBox", 2);

            COLLIDER_OF_HANDLE = colliderClass.getConstructor(int.class);
            COLLIDER_CLEAR_BOXES = colliderClass.getMethod("clearBoxes");
        } catch (final ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    public static final int NO_BODY = -1;
    public static final long NO_SCENE = 0L;

    public static long sceneHandle(final ServerLevel level) {
        if (level == null) return NO_SCENE;
        try {
            final Object handle = GET_SCENE_HANDLE.invoke(null, level);
            return handle instanceof final Number number ? number.longValue() : NO_SCENE;
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            return NO_SCENE;
        }
    }

    public static int bodyId(final ServerSubLevel subLevel) {
        if (subLevel == null) return NO_BODY;
        try {
            final Object id = GET_ID.invoke(null, subLevel);
            return id instanceof final Number number ? number.intValue() : NO_BODY;
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            return NO_BODY;
        }
    }

    public static void removeConstraint(final long sceneHandle, final long constraintHandle) {
        try {
            REMOVE_CONSTRAINT.invoke(null, sceneHandle, constraintHandle);
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to remove Sable Rapier constraint", exception);
        }
    }

    // TODO(belts): args are (friction, volume, restitution, liquid, callback). Sable bakes those
    // per BlockState in RapierVoxelColliderBakery#buildPhysicsDataForBlock; scaled sublevels skip
    // that path (RapierPhysicsPipelineMixin cancels handleChunkSectionAddition/handleBlockChange),
    // so the null callback drops every BlockSubLevelCollisionCallback: Create belts used as tracks
    // lose their tangent surface velocity and spin in place, and impact handling dies for bells,
    // TNT and fragile blocks. Per-block friction and restitution are flattened here too.
    // Wiring it back needs three things: the source BlockPos carried per cell through
    // ColliderCompiler (cells are binned at contracted coords, so cell != BlockPos and
    // BeltBlockCallback's getBlockEntity lookup lands on the wrong block), the returned tangent
    // velocity multiplied by scale to reach the contracted metric frame, and ShapeRegistry
    // interning widened past pure geometry so two identical boxes can carry different callbacks.
    public static ColliderHandle createCollider(final ColliderShapeKey shape) {
        try {
            final double volume = shape.volume();
            PocketTrace.enter("Rapier3D.createVoxelColliderEntry",
                    "volume=" + volume, "boxes=" + shape.faces().size());
            final Object collider = CREATE_VOXEL_COLLIDER_ENTRY.invoke(null, 1.0D, volume, 0.0D, false, null);
            PocketTrace.exit("Rapier3D.createVoxelColliderEntry");

            addBoxes(collider, shape);

            final int handle = ((Number) COLLIDER_HANDLE.invoke(collider)).intValue();
            return new ColliderHandle(handle);
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create Sable Rapier voxel collider", exception);
        }
    }

    private static void addBoxes(final Object collider, final ColliderShapeKey shape)
            throws ReflectiveOperationException {
        for (final ColliderShapeKey.Face face : shape.faces()) {
            COLLIDER_ADD_BOX.invoke(
                    collider,
                    new Vector3d(face.minXd(), face.minYd(), face.minZd()),
                    new Vector3d(face.maxXd(), face.maxYd(), face.maxZd())
            );
        }
    }

    public static void reprogramCollider(final int handle, final ColliderShapeKey shape) {
        try {
            final Object collider = COLLIDER_OF_HANDLE.newInstance(handle);

            PocketTrace.enter("RapierVoxelColliderData.clearBoxes", "handle=" + handle);
            COLLIDER_CLEAR_BOXES.invoke(collider);
            PocketTrace.exit("RapierVoxelColliderData.clearBoxes");

            addBoxes(collider, shape);
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to reprogram Sable Rapier voxel collider", exception);
        }
    }

    public static void addSubLevelChunk(
            final ServerLevel level,
            final ServerSubLevel subLevel,
            final int sectionX,
            final int sectionY,
            final int sectionZ,
            final int[] data
    ) {
        try {
            final long scene = scene(level);
            final int id = ((Number) GET_ID.invoke(null, subLevel)).intValue();

            if (PocketTrace.PHYSICS) {
                PocketTrace.enter("Rapier3D.addChunk",
                        "scene=0x" + Long.toHexString(scene),
                        "bodyId=" + id,
                        "section=[" + sectionX + "," + sectionY + "," + sectionZ + "]",
                        "occupied=" + countOccupied(data),
                        "handles=" + summariseHandles(data),
                        PocketTrace.context(subLevel));
            } else {
                PocketTrace.mark("Rapier3D.addChunk bodyId=" + id
                        + " section=[" + sectionX + "," + sectionY + "," + sectionZ + "]");
            }
            ADD_CHUNK.invoke(null, scene, sectionX, sectionY, sectionZ, data, false, id);
            PocketTrace.exit("Rapier3D.addChunk section=["
                    + sectionX + "," + sectionY + "," + sectionZ + "] bodyId=" + id);
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to upload scaled Sable Rapier chunk", exception);
        }
    }

    public static void changeSubLevelBlock(
            final ServerLevel level,
            final int x,
            final int y,
            final int z,
            final int packedState
    ) {
        try {
            CHANGE_BLOCK.invoke(null, scene(level), x, y, z, packedState);
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to mutate Sable Rapier sub-level cell", exception);
        }
    }

    public static void removeSubLevelChunk(
            final ServerLevel level,
            final int sectionX,
            final int sectionY,
            final int sectionZ
    ) {
        try {
            final long scene = scene(level);
            PocketTrace.enter("Rapier3D.removeChunk",
                    "scene=0x" + Long.toHexString(scene),
                    "section=[" + sectionX + "," + sectionY + "," + sectionZ + "]",
                    "thread=" + Thread.currentThread().getName());
            REMOVE_CHUNK.invoke(null, scene, sectionX, sectionY, sectionZ, false);
            PocketTrace.exit("Rapier3D.removeChunk section=["
                    + sectionX + "," + sectionY + "," + sectionZ + "]");
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to remove Sable Rapier sub-level chunk", exception);
        }
    }

    public static void setLocalBounds(
            final ServerLevel level,
            final ServerSubLevel subLevel,
            final int minX,
            final int minY,
            final int minZ,
            final int maxX,
            final int maxY,
            final int maxZ
    ) {
        setLocalBounds(level, subLevel, minX, minY, minZ, maxX, maxY, maxZ, true);
    }

    public static void setLocalBoundsQuiet(
            final ServerLevel level,
            final ServerSubLevel subLevel,
            final int minX,
            final int minY,
            final int minZ,
            final int maxX,
            final int maxY,
            final int maxZ
    ) {
        setLocalBounds(level, subLevel, minX, minY, minZ, maxX, maxY, maxZ, false);
    }

    private static void setLocalBounds(
            final ServerLevel level,
            final ServerSubLevel subLevel,
            final int minX,
            final int minY,
            final int minZ,
            final int maxX,
            final int maxY,
            final int maxZ,
            final boolean trace
    ) {
        try {
            final long scene = scene(level);
            final int id = ((Number) GET_ID.invoke(null, subLevel)).intValue();
            if (trace) {
                PocketTrace.enter("Rapier3D.setLocalBounds",
                        "scene=0x" + Long.toHexString(scene),
                        "bodyId=" + id,
                        "min=[" + minX + "," + minY + "," + minZ + "]",
                        "max=[" + maxX + "," + maxY + "," + maxZ + "]",
                        PocketTrace.context(subLevel));
            }
            SET_LOCAL_BOUNDS.invoke(
                    null,
                    scene, id,
                    minX, minY, minZ,
                    maxX, maxY, maxZ
            );
            if (trace) PocketTrace.exit("Rapier3D.setLocalBounds bodyId=" + id);
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to set scaled Sable Rapier local bounds", exception);
        }
    }

    private static long scene(final ServerLevel level) throws ReflectiveOperationException {
        return ((Number) GET_SCENE_HANDLE.invoke(null, level)).longValue();
    }

    private static int countOccupied(final int[] data) {
        int occupied = 0;
        for (final int packed : data) {
            if (packed != 0) occupied++;
        }
        return occupied;
    }

    private static String summariseHandles(final int[] data) {
        final java.util.Set<Integer> handles = new java.util.TreeSet<>();
        for (final int packed : data) {
            if (packed == 0) continue;
            handles.add((packed >>> 16) - 1);
        }
        return handles.toString();
    }

    private static Method find(final Class<?> owner, final String name, final int parameterCount) throws NoSuchMethodException {
        Method found = null;
        for (final Method method : owner.getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != parameterCount) continue;
            if (found != null) {
                throw new NoSuchMethodException(
                        "Ambiguous Sable Rapier method " + owner.getName() + "#" + name + "/" + parameterCount
                                + ": matched both " + found + " and " + method
                                + ". Pocket Sized's version-pinned bridge needs updating."
                );
            }
            found = method;
        }

        if (found == null) {
            throw new NoSuchMethodException(owner.getName() + "#" + name + "/" + parameterCount);
        }
        found.setAccessible(true);
        return found;
    }

    public record ColliderHandle(int handle) {}

    private RapierBridge() {}
}
