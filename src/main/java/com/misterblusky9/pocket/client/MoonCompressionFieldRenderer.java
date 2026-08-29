package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.moon.MoonScaleNetwork;
import com.misterblusky9.pocket.moon.MoonTargeting;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class MoonCompressionFieldRenderer {
    private static final int GRID = 10;
    private static final float HALF_SIZE = (float) MoonTargeting.SURFACE_HALF_SIZE;
    private static final float CELL_SIZE = HALF_SIZE * 2.0F / GRID;
    private static final float PLANE = -(float) MoonTargeting.PLANE_DISTANCE;

    private static final float INITIAL_SPEED_FRACTION = 0.35F;
    private static final float CLIMB_TICKS = 9.0F;
    private static final int SHAPE_SAMPLES = 64;
    private static final float MAX_STALL_DEPTH = 0.72F;
    private static final float GRIND_FAST = 2.30F;
    private static final float GRIND_SLOW = 0.83F;
    private static final float RETRACT_SPEED_MULTIPLIER = 4.2F;
    private static final float FRONT_WIDTH = 2.6F;
    private static final float JITTER_AMOUNT = 0.45F;
    private static final float STRAIN_BOOST = 0.55F;

    private static final int PULSE_TRAVEL_TICKS = 5;
    private static final float PULSE_WIDTH = 1.15F * CELL_SIZE;

    private static final float[] SHRINK_SHEEN = { 0.05F, 0.42F, 0.62F, 0.22F };
    private static final float[] SHRINK_FRONT = { 0.55F, 0.93F, 1.00F, 0.95F };
    private static final float[] GROW_SHEEN = { 0.60F, 0.44F, 0.04F, 0.22F };
    private static final float[] GROW_FRONT = { 1.00F, 0.90F, 0.45F, 0.95F };

    private static final Queue<MoonScaleNetwork.MoonEffectPayload> PENDING =
            new ConcurrentLinkedQueue<>();

    private static Field active;

    public static void accept(final MoonScaleNetwork.MoonEffectPayload payload) {
        if (payload != null) PENDING.add(payload);
    }

    public static boolean isGripped() {
        return active != null && active.phase != Phase.RELEASING;
    }

    public static boolean isSealed() {
        return active != null && active.phase == Phase.SEALED;
    }

    public static boolean isGrowing() {
        return active != null && active.growing;
    }

    public static float progress() {
        return active == null ? 0.0F : active.progress;
    }

    public static void render(
            final Matrix4f frustumMatrix,
            final Matrix4f projectionMatrix,
            final float partialTick
    ) {
        final Minecraft minecraft = Minecraft.getInstance();
        final Level level = minecraft.level;
        if (level == null) {
            clear();
            return;
        }

        drain(level);

        final Field field = active;
        if (field == null) return;
        if (field.level != level) {
            clear();
            return;
        }
        if (!level.dimensionType().hasSkyLight()) return;

        if (field.mesh == null) {
            build(field);
            if (field.mesh == null) {
                active = null;
                return;
            }
        }

        final float renderTicks = AnimationTickHolder.getRenderTime(level);
        advance(field, renderTicks, resistanceOf(MoonScaleClient.get()));

        if (field.phase == Phase.RELEASING && field.front <= 0.0F) {
            field.dispose();
            active = null;
            return;
        }

        final var shader = PocketShaders.compressionField();
        if (shader == null) return;

        final Matrix4f modelView = new Matrix4f(frustumMatrix)
                .rotateY((float) (-Math.PI * 0.5D))
                .rotateX(level.getSunAngle(partialTick))
                .scale(MoonScaleClient.get(), 1.0F, MoonScaleClient.get());

        float strain = 0.0F;
        if (field.phase == Phase.SEALED) {
            final float grind = 0.5F + 0.5F * Mth.sin(renderTicks * GRIND_FAST * 1.3F);
            final float shudder = 0.5F + 0.5F * Mth.sin(renderTicks * GRIND_SLOW * 2.7F + 0.9F);
            strain = STRAIN_BOOST * (0.25F + 0.75F * grind * grind * shudder)
                    * (0.35F + 0.65F * resistanceOf(MoonScaleClient.get()));
        }

        final CompressionFieldMesh mesh =
                field.phase == Phase.SEALED && field.sealedMesh != null
                        ? field.sealedMesh
                        : field.mesh;

        mesh.draw(
                shader,
                modelView,
                projectionMatrix,
                new Vector3f(0.0F, 0.0F, 0.0F),
                new Vector3f(0.0F, 0.0F, 0.0F),
                0.02F,
                field.front,
                FRONT_WIDTH,
                new Vector3f(field.seedX * HALF_SIZE, PLANE, field.seedZ * HALF_SIZE),
                pulseRadius(field, renderTicks),
                PULSE_WIDTH,
                CELL_SIZE,
                strain,
                renderTicks,
                0.0F,
                field.growing ? GROW_SHEEN : SHRINK_SHEEN,
                field.growing ? GROW_FRONT : SHRINK_FRONT
        );

        drawSurfaceTint(modelView, field, renderTicks, strain);
    }

    private static void drawSurfaceTint(
            final Matrix4f modelView,
            final Field field,
            final float renderTicks,
            final float strain
    ) {
        final float[] sheen = field.growing ? GROW_SHEEN : SHRINK_SHEEN;
        final float[] front = field.growing ? GROW_FRONT : SHRINK_FRONT;
        final float seedX = field.seedX * HALF_SIZE;
        final float seedZ = field.seedZ * HALF_SIZE;
        final float pulse = pulseRadius(field, renderTicks);

        BufferBuilder builder = null;
        for (int x = 0; x < GRID; x++) {
            final float minX = -HALF_SIZE + x * CELL_SIZE;
            final float maxX = minX + CELL_SIZE;
            final float centerX = (minX + maxX) * 0.5F;

            for (int z = 0; z < GRID; z++) {
                final float minZ = -HALF_SIZE + z * CELL_SIZE;
                final float maxZ = minZ + CELL_SIZE;
                final float centerZ = (minZ + maxZ) * 0.5F;

                final float dx = (centerX - seedX) / CELL_SIZE;
                final float dz = (centerZ - seedZ) / CELL_SIZE;
                final float distance = Math.max(
                        0.0F,
                        Mth.sqrt(dx * dx + dz * dz) + jitter(x, z) * JITTER_AMOUNT
                );

                final float behind = field.front - distance;
                if (behind < 0.0F) continue;

                final float edge = 1.0F - Mth.clamp(behind / FRONT_WIDTH, 0.0F, 1.0F);
                final float edge2 = edge * edge;
                final float frontMix = edge2;

                float red = Mth.lerp(frontMix, sheen[0], front[0]);
                float green = Mth.lerp(frontMix, sheen[1], front[1]);
                float blue = Mth.lerp(frontMix, sheen[2], front[2]);

                final float rim = (float) Math.pow(edge, 12.0D) * 0.92F;
                red = Mth.lerp(Mth.clamp(rim * 0.82F, 0.0F, 1.0F), red, 1.0F);
                green = Mth.lerp(Mth.clamp(rim * 0.82F, 0.0F, 1.0F), green, 1.0F);
                blue = Mth.lerp(Mth.clamp(rim * 0.82F, 0.0F, 1.0F), blue, 1.0F);

                float alpha = 0.10F + 0.38F * edge2;
                alpha *= 1.0F + Math.max(0.0F, strain) * 0.35F;

                if (pulse > -999.0F) {
                    final float rx = centerX - seedX;
                    final float rz = centerZ - seedZ;
                    final float radius = Mth.sqrt(rx * rx + rz * rz);
                    final float band = 1.0F - Mth.clamp(Math.abs(pulse - radius) / PULSE_WIDTH, 0.0F, 1.0F);
                    final float discharge = band * band;
                    red = Mth.lerp(discharge, red, 1.0F);
                    green = Mth.lerp(discharge, green, 1.0F);
                    blue = Mth.lerp(discharge, blue, 1.0F);
                    alpha = Math.max(alpha, discharge * 0.52F);
                }

                alpha = Mth.clamp(alpha, 0.0F, 0.58F);
                final float plane = PLANE + 0.05F;

                if (builder == null) {
                    builder = Tesselator.getInstance().begin(
                            VertexFormat.Mode.QUADS,
                            DefaultVertexFormat.POSITION_COLOR
                    );
                }
                builder.addVertex(modelView, minX, plane, maxZ).setColor(red, green, blue, alpha);
                builder.addVertex(modelView, maxX, plane, maxZ).setColor(red, green, blue, alpha);
                builder.addVertex(modelView, maxX, plane, minZ).setColor(red, green, blue, alpha);
                builder.addVertex(modelView, minX, plane, minZ).setColor(red, green, blue, alpha);
            }
        }

        if (builder == null) return;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        );
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    public static void clear() {
        final Field field = active;
        active = null;
        PENDING.clear();
        if (field != null) field.dispose();
    }

    private static void drain(final Level level) {
        MoonScaleNetwork.MoonEffectPayload payload;
        while ((payload = PENDING.poll()) != null) {
            switch (payload.action()) {
                case MoonScaleNetwork.EFFECT_BEGIN -> begin(level, payload);
                case MoonScaleNetwork.EFFECT_RELEASE -> release();
                case MoonScaleNetwork.EFFECT_PULSE -> pulse(level);
                default -> {
                }
            }
        }
    }

    private static void begin(
            final Level level,
            final MoonScaleNetwork.MoonEffectPayload payload
    ) {
        if (active != null) active.dispose();
        final float now = AnimationTickHolder.getRenderTime(level);
        active = new Field(
                level,
                Mth.clamp(payload.surfaceX(), -1.0F, 1.0F),
                Mth.clamp(payload.surfaceZ(), -1.0F, 1.0F),
                Math.max(1, payload.acquireTicks()),
                payload.growing(),
                payload.acquired(),
                now
        );
    }

    private static void release() {
        if (active != null && active.phase != Phase.RELEASING) {
            active.phase = Phase.RELEASING;
        }
    }

    private static void pulse(final Level level) {
        if (active != null) active.lastPulseTick = AnimationTickHolder.getRenderTime(level);
    }

    private static void build(final Field field) {
        final List<CompressionFieldMesh.Face> faces = new ArrayList<>();
        final List<CompressionFieldMesh.Face> sealed = new ArrayList<>();
        float maxDistance = 0.0F;

        final float seedX = field.seedX * HALF_SIZE;
        final float seedZ = field.seedZ * HALF_SIZE;

        for (int x = 0; x < GRID; x++) {
            final float minX = -HALF_SIZE + x * CELL_SIZE;
            final float maxX = minX + CELL_SIZE;
            final float centerX = (minX + maxX) * 0.5F;

            for (int z = 0; z < GRID; z++) {
                final float minZ = -HALF_SIZE + z * CELL_SIZE;
                final float maxZ = minZ + CELL_SIZE;
                final float centerZ = (minZ + maxZ) * 0.5F;

                final float dx = (centerX - seedX) / CELL_SIZE;
                final float dz = (centerZ - seedZ) / CELL_SIZE;
                final float distance = Math.max(
                        0.0F,
                        Mth.sqrt(dx * dx + dz * dz) + jitter(x, z) * JITTER_AMOUNT
                );
                maxDistance = Math.max(maxDistance, distance);

                faces.add(new CompressionFieldMesh.Face(
                        Direction.UP,
                        PLANE,
                        minX,
                        maxX,
                        minZ,
                        maxZ,
                        distance
                ));
                sealed.add(new CompressionFieldMesh.Face(
                        Direction.UP,
                        PLANE,
                        minX,
                        maxX,
                        minZ,
                        maxZ,
                        0.0F
                ));
            }
        }

        field.maxDistance = Math.max(1.0F, maxDistance);
        field.mesh = CompressionFieldMesh.upload(faces);
        field.sealedMesh = CompressionFieldMesh.upload(sealed);
    }

    private static void advance(
            final Field field,
            final float renderTicks,
            final float resistance
    ) {
        final float deltaTicks = field.lastRenderTick < 0.0F
                ? 0.0F
                : Mth.clamp(renderTicks - field.lastRenderTick, 0.0F, 4.0F);
        field.lastRenderTick = renderTicks;

        if (field.phase == Phase.RELEASING) {
            final float retractPerTick = field.maxDistance / field.acquireTicks
                    * RETRACT_SPEED_MULTIPLIER;
            field.front = Math.max(0.0F, field.front - retractPerTick * deltaTicks);
            field.progress = Mth.clamp(field.front / field.maxDistance, 0.0F, 1.0F);
            return;
        }

        final float elapsed = Math.max(0.0F, renderTicks - field.startRenderTick);
        final float u = Mth.clamp(elapsed / field.acquireTicks, 0.0F, 1.0F);

        if (field.acquired) {
            field.front = Math.max(field.front, u * field.maxDistance);
        } else {
            if (field.shape == null) field.shape = buildShape(field, resistance);
            field.front = Math.max(field.front, sampleShape(field.shape, u) * field.maxDistance);
        }

        if (u >= 1.0F) {
            field.front = field.maxDistance;
            if (field.phase == Phase.ACQUIRING) field.phase = Phase.SEALED;
        }

        field.progress = Mth.clamp(field.front / field.maxDistance, 0.0F, 1.0F);
    }

    private static float[] buildShape(final Field field, final float resistance) {
        final float[] cumulative = new float[SHAPE_SAMPLES];
        final float step = field.acquireTicks / (SHAPE_SAMPLES - 1.0F);

        float total = 0.0F;
        for (int i = 1; i < SHAPE_SAMPLES; i++) {
            final float t = i * step;
            final float climb = INITIAL_SPEED_FRACTION
                    + (1.0F - INITIAL_SPEED_FRACTION) * Mth.clamp(t / CLIMB_TICKS, 0.0F, 1.0F);
            final float fast = 0.5F + 0.5F * Mth.sin(t * GRIND_FAST);
            final float slow = 0.5F + 0.5F * Mth.sin(t * GRIND_SLOW + 1.7F);
            final float stall = MAX_STALL_DEPTH * resistance * (0.35F + 0.65F * fast * slow);
            total += Math.max(0.08F, climb * (1.0F - stall));
            cumulative[i] = total;
        }

        if (total <= 1.0E-5F) {
            for (int i = 0; i < SHAPE_SAMPLES; i++) {
                cumulative[i] = i / (SHAPE_SAMPLES - 1.0F);
            }
            return cumulative;
        }

        for (int i = 0; i < SHAPE_SAMPLES; i++) cumulative[i] /= total;
        return cumulative;
    }

    private static float sampleShape(final float[] shape, final float u) {
        final float scaled = Mth.clamp(u, 0.0F, 1.0F) * (shape.length - 1);
        final int index = (int) scaled;
        if (index >= shape.length - 1) return shape[shape.length - 1];
        return Mth.lerp(scaled - index, shape[index], shape[index + 1]);
    }

    private static float resistanceOf(final float scale) {
        if (!(scale > 0.0F) || scale >= 1.0F) return 0.0F;
        final double depth = -Math.log(scale) / Math.log(2.0D);
        return Mth.clamp((float) (depth / 4.0D), 0.0F, 1.0F);
    }

    private static float pulseRadius(final Field field, final float renderTicks) {
        if (field.lastPulseTick < 0.0F) return -1000.0F;
        final float age = renderTicks - field.lastPulseTick;
        if (age < 0.0F || age > PULSE_TRAVEL_TICKS + PULSE_WIDTH) return -1000.0F;
        return age / PULSE_TRAVEL_TICKS * HALF_SIZE * 2.2F;
    }

    private static float jitter(final int x, final int z) {
        int h = x * 0x1f1f1f1f ^ z * 0x6c8e9cf5;
        h ^= h >>> 16;
        h *= 0x7feb352d;
        h ^= h >>> 15;
        h *= 0x846ca68b;
        h ^= h >>> 16;
        return ((h & 0xFFFF) / 65535.0F) * 2.0F - 1.0F;
    }

    private enum Phase {
        ACQUIRING,
        SEALED,
        RELEASING
    }

    private static final class Field {
        private final Level level;
        private final float seedX;
        private final float seedZ;
        private final int acquireTicks;
        private final boolean growing;
        private final boolean acquired;
        private final float startRenderTick;

        private float lastRenderTick;
        private float front;
        private float progress;
        private float maxDistance = 1.0F;
        private float lastPulseTick = -1.0F;
        private float[] shape;
        private Phase phase = Phase.ACQUIRING;
        private CompressionFieldMesh mesh;
        private CompressionFieldMesh sealedMesh;

        private Field(
                final Level level,
                final float seedX,
                final float seedZ,
                final int acquireTicks,
                final boolean growing,
                final boolean acquired,
                final float startRenderTick
        ) {
            this.level = level;
            this.seedX = seedX;
            this.seedZ = seedZ;
            this.acquireTicks = acquireTicks;
            this.growing = growing;
            this.acquired = acquired;
            this.startRenderTick = startRenderTick;
            this.lastRenderTick = startRenderTick;
        }

        private void dispose() {
            if (this.mesh != null) {
                this.mesh.close();
                this.mesh = null;
            }
            if (this.sealedMesh != null) {
                this.sealedMesh.close();
                this.sealedMesh = null;
            }
        }
    }

    private MoonCompressionFieldRenderer() {}
}
