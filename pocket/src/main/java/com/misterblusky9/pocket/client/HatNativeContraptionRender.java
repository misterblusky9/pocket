package com.misterblusky9.pocket.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import dev.eriksonn.aeronautics.content.blocks.propeller.bearing.contraption.PropellerBearingContraptionEntity;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.joml.Vector3dc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HatNativeContraptionRender {
    private static final Map<UUID, Map<UUID, Preview>> ALL = new HashMap<>();

    private static final class Preview {
        UUID part;
        AbstractContraptionEntity model;
        double x, y, z;
        float lastAngle, angle;
        long receivedTick;

        Preview(UUID part, AbstractContraptionEntity model) {
            this.part = part;
            this.model = model;
        }
    }

    public static void accept(UUID token, CompoundTag states) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Map<UUID, Preview> previews = ALL.computeIfAbsent(token, ignored -> new HashMap<>());
        Set<UUID> current = new HashSet<>();
        for (Tag tag : states.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) tag;
            if (!entry.hasUUID("uuid") || !entry.hasUUID("part")) continue;
            UUID id = entry.getUUID("uuid");
            UUID part = entry.getUUID("part");
            current.add(id);
            Preview preview = previews.get(id);
            if (preview == null && entry.contains("entity", Tag.TAG_COMPOUND)) {
                try {
                    Entity entity = EntityType.loadEntityRecursive(entry.getCompound("entity"), level, loaded -> loaded);
                    if (!(entity instanceof AbstractContraptionEntity contraption) || contraption.getContraption() == null)
                        continue;
                    preview = new Preview(part, contraption);
                    preview.angle = entry.getFloat("angle");
                    preview.lastAngle = preview.angle;
                    previews.put(id, preview);
                } catch (RuntimeException exception) {
                    LogUtils.getLogger().warn("Cannot open hat native contraption {}", id, exception);
                    continue;
                }
            }
            if (preview == null) continue;
            preview.part = part;
            preview.x = entry.getDouble("x");
            preview.y = entry.getDouble("y");
            preview.z = entry.getDouble("z");
            preview.model.setPos(preview.x, preview.y, preview.z);
            if (preview.model instanceof PropellerBearingContraptionEntity propeller
                    && entry.contains("aero_facing", Tag.TAG_INT)) {
                propeller.direction = Direction.from3DDataValue(entry.getInt("aero_facing"));
                propeller.previousTiltQuat.set(propeller.tiltQuat);
                propeller.tiltQuat.set(entry.getFloat("aero_qx"), entry.getFloat("aero_qy"),
                        entry.getFloat("aero_qz"), entry.getFloat("aero_qw"));
            }
            if (entry.getBoolean("controlled")) {
                preview.lastAngle = preview.angle;
                preview.angle = entry.getFloat("angle");
                preview.receivedTick = level.getGameTime();
            }
        }
        previews.keySet().retainAll(current);
        if (previews.isEmpty()) ALL.remove(token);
    }

    public static void draw(UUID token, ClientSubLevel part, Vector3dc origin,
                            PoseStack pose, MultiBufferSource buffers, int light) {
        Map<UUID, Preview> previews = ALL.get(token);
        if (previews == null || previews.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || part.isRemoved()) return;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        for (Map.Entry<UUID, Preview> entry : previews.entrySet()) {
            Preview preview = entry.getValue();
            if (!part.getUniqueId().equals(preview.part)) continue;
            AbstractContraptionEntity model = preview.model;
            Contraption contraption = model.getContraption();
            if (contraption == null || !model.isReadyForRender()) continue;
            pose.pushPose();
            pose.translate(preview.x - origin.x(), preview.y - origin.y(), preview.z - origin.z());
            try {
                if (model instanceof ControlledContraptionEntity controlled) {
                    float step = preview.receivedTick == level.getGameTime() ? partial : 1.0F;
                    controlled.setAngle(Mth.rotLerp(step, preview.lastAngle, preview.angle));
                }
                model.applyLocalTransforms(pose, 1.0F);
                var renderWorld = contraption.getOrCreateClientContraptionLazy().getRenderLevel();
                for (RenderType layer : RenderType.chunkBufferLayers()) {
                    SuperByteBuffer mesh = ContraptionEntityRenderer.getBuffer(contraption, renderWorld, layer);
                    if (!mesh.isEmpty()) mesh.light(light).renderInto(pose, buffers.getBuffer(layer));
                }
            } catch (RuntimeException exception) {
                LogUtils.getLogger().debug("Hat native contraption {} render failed", entry.getKey(), exception);
            } finally {
                pose.popPose();
            }
        }
    }

    public static boolean drawnByPreview(UUID token, Entity entity) {
        Map<UUID, Preview> previews = ALL.get(token);
        return previews != null && entity instanceof AbstractContraptionEntity && previews.containsKey(entity.getUUID());
    }

    public static void forget(UUID token) {
        ALL.remove(token);
    }

    public static void clear() {
        ALL.clear();
    }

    private HatNativeContraptionRender() {}
}
