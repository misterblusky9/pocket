package com.misterblusky9.pym.internal.physics;

import com.misterblusky9.pym.internal.debug.PymTrace;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RapierBridge {
    private record SyncedMass(int bodyId, double scale) {}
    private static final Map<UUID, SyncedMass> SYNCED_MASS = new ConcurrentHashMap<>();

    private static final Class<?> RAPIER_3D;
    private static final Method CREATE_VOXEL_COLLIDER_ENTRY;
    private static final Method REMOVE_CONSTRAINT;
    private static final Method ADD_CHUNK;
    private static final Method REMOVE_CHUNK;
    private static final Method CHANGE_BLOCK;
    private static final Method GET_SCENE_HANDLE;
    private static final Method GET_ID;
    private static final Method SET_LOCAL_BOUNDS;
    private static final Method SET_CENTER_OF_MASS;
    private static final Method SET_MASS_PROPERTIES_FROM;
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
            SET_CENTER_OF_MASS = find(RAPIER_3D, "setCenterOfMass", 5);
            SET_MASS_PROPERTIES_FROM = find(RAPIER_3D, "setMassPropertiesFrom", 3);

            Class<?> colliderClass = Class.forName("dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderData");
            COLLIDER_HANDLE = colliderClass.getMethod("handle");
            COLLIDER_ADD_BOX = find(colliderClass, "addBox", 2);

            COLLIDER_OF_HANDLE = colliderClass.getConstructor(int.class);
            COLLIDER_CLEAR_BOXES = colliderClass.getMethod("clearBoxes");
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    public static final int NO_BODY = -1;
    public static final long NO_SCENE = 0L;

    public static long sceneHandle(ServerLevel level) {
        if (level == null) return NO_SCENE;
        try {
            Object handle = GET_SCENE_HANDLE.invoke(null, level);
            return handle instanceof Number number ? number.longValue() : NO_SCENE;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return NO_SCENE;
        }
    }

    public static int bodyId(ServerSubLevel subLevel) {
        if (!isLive(subLevel)) return NO_BODY;
        try {
            Object id = GET_ID.invoke(null, subLevel);
            return id instanceof Number number ? number.intValue() : NO_BODY;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return NO_BODY;
        }
    }

    public static boolean isLive(ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved() || subLevel.getUniqueId() == null) return false;
        var container = SubLevelContainer.getContainer(subLevel.getLevel());
        return container != null
                && container.getSubLevel(subLevel.getUniqueId()) == subLevel
                && sceneHandle(subLevel.getLevel()) != NO_SCENE;
    }

    public static void removeConstraint(long sceneHandle, long constraintHandle) {
        try {
            REMOVE_CONSTRAINT.invoke(null, sceneHandle, constraintHandle);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to remove Sable Rapier constraint", exception);
        }
    }

    public static ColliderHandle createCollider(ColliderShapeKey shape) {
        try {
            double volume = shape.volume();
            PymTrace.enter("Rapier3D.createVoxelColliderEntry",
                    "volume=" + volume, "boxes=" + shape.faces().size(), "material=" + shape.material());
            ColliderMaterial material = shape.material();
            Object collider = CREATE_VOXEL_COLLIDER_ENTRY.invoke(
                    null, material.friction(), volume, material.restitution(), false, null);
            PymTrace.exit("Rapier3D.createVoxelColliderEntry");

            addBoxes(collider, shape);

            int handle = ((Number) COLLIDER_HANDLE.invoke(collider)).intValue();
            return new ColliderHandle(handle);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create Sable Rapier voxel collider", exception);
        }
    }

    private static void addBoxes(Object collider, ColliderShapeKey shape)
            throws ReflectiveOperationException {
        for (ColliderShapeKey.Face face : shape.faces()) {
            COLLIDER_ADD_BOX.invoke(
                    collider,
                    new Vector3d(face.minXd(), face.minYd(), face.minZd()),
                    new Vector3d(face.maxXd(), face.maxYd(), face.maxZd())
            );
        }
    }

    public static void reprogramCollider(int handle, ColliderShapeKey shape) {
        try {
            Object collider = COLLIDER_OF_HANDLE.newInstance(handle);

            PymTrace.enter("RapierVoxelColliderData.clearBoxes", "handle=" + handle);
            COLLIDER_CLEAR_BOXES.invoke(collider);
            PymTrace.exit("RapierVoxelColliderData.clearBoxes");

            addBoxes(collider, shape);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to reprogram Sable Rapier voxel collider", exception);
        }
    }

    public static void addSubLevelChunk(
            ServerLevel level,
            ServerSubLevel subLevel,
            int sectionX,
            int sectionY,
            int sectionZ,
            int[] data
    ) {
        try {
            long scene = sceneHandle(level);
            int id = bodyId(subLevel);
            if (scene == NO_SCENE || id == NO_BODY) return;

            if (PymTrace.PHYSICS) {
                PymTrace.enter("Rapier3D.addChunk",
                        "scene=0x" + Long.toHexString(scene),
                        "bodyId=" + id,
                        "section=[" + sectionX + "," + sectionY + "," + sectionZ + "]",
                        "occupied=" + countOccupied(data),
                        "handles=" + summariseHandles(data),
                        PymTrace.context(subLevel));
            } else {
                PymTrace.mark("Rapier3D.addChunk bodyId=" + id
                        + " section=[" + sectionX + "," + sectionY + "," + sectionZ + "]");
            }
            ADD_CHUNK.invoke(null, scene, sectionX, sectionY, sectionZ, data, false, id);
            PymTrace.exit("Rapier3D.addChunk section=["
                    + sectionX + "," + sectionY + "," + sectionZ + "] bodyId=" + id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to upload scaled Sable Rapier chunk", exception);
        }
    }

    public static void changeSubLevelBlock(
            ServerLevel level,
            int x,
            int y,
            int z,
            int packedState
    ) {
        try {
            CHANGE_BLOCK.invoke(null, scene(level), x, y, z, packedState);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to mutate Sable Rapier sub-level cell", exception);
        }
    }

    public static void removeSubLevelChunk(
            ServerLevel level,
            int sectionX,
            int sectionY,
            int sectionZ
    ) {
        try {
            long scene = scene(level);
            PymTrace.enter("Rapier3D.removeChunk",
                    "scene=0x" + Long.toHexString(scene),
                    "section=[" + sectionX + "," + sectionY + "," + sectionZ + "]",
                    "thread=" + Thread.currentThread().getName());
            REMOVE_CHUNK.invoke(null, scene, sectionX, sectionY, sectionZ, false);
            PymTrace.exit("Rapier3D.removeChunk section=["
                    + sectionX + "," + sectionY + "," + sectionZ + "]");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to remove Sable Rapier sub-level chunk", exception);
        }
    }

    public static void setLocalBounds(
            ServerLevel level,
            ServerSubLevel subLevel,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        setLocalBounds(level, subLevel, minX, minY, minZ, maxX, maxY, maxZ, true);
    }

    public static void setLocalBoundsQuiet(
            ServerLevel level,
            ServerSubLevel subLevel,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        setLocalBounds(level, subLevel, minX, minY, minZ, maxX, maxY, maxZ, false);
    }

    private static void setLocalBounds(
            ServerLevel level,
            ServerSubLevel subLevel,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            boolean trace
    ) {
        try {
            long scene = sceneHandle(level);
            int id = bodyId(subLevel);
            if (scene == NO_SCENE || id == NO_BODY) return;
            if (trace) {
                PymTrace.enter("Rapier3D.setLocalBounds",
                        "scene=0x" + Long.toHexString(scene),
                        "bodyId=" + id,
                        "min=[" + minX + "," + minY + "," + minZ + "]",
                        "max=[" + maxX + "," + maxY + "," + maxZ + "]",
                        PymTrace.context(subLevel));
            }
            SET_LOCAL_BOUNDS.invoke(
                    null,
                    scene, id,
                    minX, minY, minZ,
                    maxX, maxY, maxZ
            );
            if (trace) PymTrace.exit("Rapier3D.setLocalBounds bodyId=" + id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to set scaled Sable Rapier local bounds", exception);
        }
    }

    public static void syncMassProperties(
            ServerSubLevel subLevel,
            double scale,
            boolean updateCenterOfMass
    ) {
        if (subLevel == null || subLevel.getLevel() == null) return;
        MassData raw = subLevel.getMassTracker();
        if (raw == null) return;
        Vector3dc center = raw.getCenterOfMass();
        if (center == null) return;
        try {
            long scene = sceneHandle(subLevel.getLevel());
            int id = bodyId(subLevel);
            if (scene == NO_SCENE || id == NO_BODY) return;
            if (updateCenterOfMass) {
                SET_CENTER_OF_MASS.invoke(null, scene, id, center.x(), center.y(), center.z());
            }
            SET_MASS_PROPERTIES_FROM.invoke(
                    null, scene, id, new ScaledMassData(raw, scale, CoupledMass.solverFloor(subLevel), CoupledMass.solverInertiaFloor(subLevel)));
            if (subLevel.getUniqueId() != null) SYNCED_MASS.put(subLevel.getUniqueId(), new SyncedMass(id, scale));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to sync scaled Sable Rapier mass properties", exception);
        }
    }

    public static double syncedMassScale(ServerSubLevel subLevel, double fallback) {
        if (subLevel == null || subLevel.getUniqueId() == null) return fallback;
        SyncedMass synced = SYNCED_MASS.get(subLevel.getUniqueId());
        return synced != null && synced.bodyId() == bodyId(subLevel) ? synced.scale() : fallback;
    }

    public static void forget(UUID id) {
        if (id != null) SYNCED_MASS.remove(id);
    }

    private static long scene(ServerLevel level) throws ReflectiveOperationException {
        return ((Number) GET_SCENE_HANDLE.invoke(null, level)).longValue();
    }

    private static int countOccupied(int[] data) {
        int occupied = 0;
        for (int packed : data) {
            if (packed != 0) occupied++;
        }
        return occupied;
    }

    private static String summariseHandles(int[] data) {
        java.util.Set<Integer> handles = new java.util.TreeSet<>();
        for (int packed : data) {
            if (packed == 0) continue;
            handles.add((packed >>> 16) - 1);
        }
        return handles.toString();
    }

    private static Method find(Class<?> owner, String name, int parameterCount) throws NoSuchMethodException {
        Method found = null;
        for (Method method : owner.getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != parameterCount) continue;
            if (found != null) {
                throw new NoSuchMethodException(
                        "Ambiguous Sable Rapier method " + owner.getName() + "#" + name + "/" + parameterCount
                                + ": matched both " + found + " and " + method
                                + ". Pym's version-pinned Rapier bridge needs updating for this Sable version."
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
