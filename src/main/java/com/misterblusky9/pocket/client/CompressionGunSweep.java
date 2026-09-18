package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.item.CompressionGunItem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

final class CompressionGunSweep {
    private static final Logger LOGGER = LogUtils.getLogger();

    static final float RETRACT_TICKS = 4.0F;

    private static final ResourceLocation BODY = geometry("item");
    private static final ResourceLocation COG = geometry("cog");

    private static final float CELL = 0.5F;
    private static final float FRONT_WIDTH = CELL * 3.0F / 16.0F;
    private static final float FACE_OFFSET = 0.0018F;

    private static final float SEALED_STRAIN = 0.55F;
    private static final float GRIND_FAST = 2.30F;
    private static final float GRIND_SLOW = 0.83F;

    private static final float RESCALE_22_5 = 1.0F / (float) Math.cos(Math.PI / 8.0D) - 1.0F;
    private static final float RESCALE_45 = 1.0F / (float) Math.cos(Math.PI / 4.0D) - 1.0F;

    private static final float[] SHRINK_SHEEN = { 0.05F, 0.42F, 0.62F, 0.22F };
    private static final float[] SHRINK_FRONT = { 0.55F, 0.93F, 1.00F, 0.95F };
    private static final float[] GROW_SHEEN = { 0.60F, 0.44F, 0.04F, 0.22F };
    private static final float[] GROW_FRONT = { 1.00F, 0.90F, 0.45F, 0.95F };

    private record Meshes(Part body, Part cog, float radius) {
        private void close() {
            this.body.close();
            this.cog.close();
        }
    }

    private static final int MAX_MESHES = 2;
    private static final Map<BakedModel, Meshes> MESHES =
            new LinkedHashMap<>(4, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<BakedModel, Meshes> eldest) {
                    if (size() <= MAX_MESHES) return false;
                    if (eldest.getValue() != null) eldest.getValue().close();
                    return true;
                }
            };

    private CompressionGunSweep() {}

    private static ResourceLocation geometry(final String name) {
        return ResourceLocation.fromNamespaceAndPath(
                PocketSized.MOD_ID, "models/item/compression_gun/" + name + ".json");
    }

    static float advanceSource(final float front, final boolean spooling, final float ticksUsing, final float delta) {
        if (!spooling) return Math.max(0.0F, front - delta * FRONT_WIDTH / RETRACT_TICKS);
        if (ticksUsing <= CompressionGunItem.SOURCE_TICKS) return 0.0F;
        return Math.min(FRONT_WIDTH,
                (ticksUsing - CompressionGunItem.SOURCE_TICKS) * FRONT_WIDTH / CompressionGunItem.SOURCE_IGNITE_TICKS);
    }

    static float advance(
            final BakedModel body,
            final float front,
            final boolean spooling,
            final float ticksUsing,
            final float delta
    ) {
        if (!spooling && front <= 0.0F) return 0.0F;
        final Meshes meshes = meshes(body);
        if (meshes == null) return 0.0F;

        final float full = meshes.radius() + FRONT_WIDTH;
        if (!spooling) return Math.max(0.0F, front - delta * full / RETRACT_TICKS);
        if (ticksUsing <= CompressionGunItem.SWEEP_START_TICKS) return 0.0F;
        return Math.min(full, CompressionGunItem.sweepProgress(ticksUsing) * meshes.radius());
    }

    static void render(
            final BakedModel body,
            final float source,
            final float front,
            final boolean growing,
            final ItemDisplayContext transformType,
            final float renderTicks,
            final PoseStack ms,
            final MultiBufferSource buffers,
            final Consumer<PoseStack> spinCog
    ) {
        if (!(source > 0.0F || front > 0.0F) || !inHand(transformType)) return;

        final ShaderInstance shader = PocketShaders.compressionField();
        if (shader == null) return;

        final Meshes meshes = meshes(body);
        if (meshes == null) return;

        if (buffers instanceof final MultiBufferSource.BufferSource batched) batched.endBatch();

        final float[] sheenColour = growing ? GROW_SHEEN : SHRINK_SHEEN;
        final float[] frontColour = growing ? GROW_FRONT : SHRINK_FRONT;

        set(shader, "FaceOffset", FACE_OFFSET);
        set(shader, "FrontWidth", FRONT_WIDTH);
        set(shader, "PulseRadius", -1000.0F);
        set(shader, "PulseWidth", 1.0F);
        set(shader, "PulseCell", 1.0F);
        set(shader, "Strain", strain(front >= meshes.radius(), renderTicks));
        set(shader, "ShimmerTime", renderTicks);
        set(shader, "Halted", 0.0F);

        final var grid = shader.getUniform("GridOrigin");
        if (grid != null) grid.set(0.0F, 0.0F, 0.0F);
        final var pulse = shader.getUniform("PulseOrigin");
        if (pulse != null) pulse.set(0.0F, 0.0F, 0.0F);
        final var sheen = shader.getUniform("SheenColor");
        if (sheen != null) sheen.set(sheenColour[0], sheenColour[1], sheenColour[2], sheenColour[3]);
        final var frontUniform = shader.getUniform("FrontColor");
        if (frontUniform != null) frontUniform.set(frontColour[0], frontColour[1], frontColour[2], frontColour[3]);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.polygonOffset(-1.0F, -10.0F);
        RenderSystem.enablePolygonOffset();

        final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        final Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(ms.last().pose());
        if (front > 0.0F) {
            set(shader, "FrontDistance", front);
            meshes.body().draw(shader, modelView, projection);
        }

        if (source > 0.0F) {
            set(shader, "FrontDistance", source);
            ms.pushPose();
            spinCog.accept(ms);
            meshes.cog().draw(shader, new Matrix4f(RenderSystem.getModelViewMatrix()).mul(ms.last().pose()), projection);
            ms.popPose();
        }

        RenderSystem.disablePolygonOffset();
        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static boolean inHand(final ItemDisplayContext transformType) {
        return transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    private static float strain(final boolean fired, final float renderTicks) {
        if (!fired) return 0.0F;
        final float grind = 0.5F + 0.5F * Mth.sin(renderTicks * GRIND_FAST * 1.3F);
        final float shudder = 0.5F + 0.5F * Mth.sin(renderTicks * GRIND_SLOW * 2.7F + 0.9F);
        return SEALED_STRAIN * (0.25F + 0.75F * grind * grind * shudder);
    }

    private static void set(final ShaderInstance shader, final String name, final float value) {
        final var uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(value);
    }

    private static Meshes meshes(final BakedModel body) {
        if (MESHES.containsKey(body)) return MESHES.get(body);

        final Meshes built = build();
        MESHES.put(body, built);
        return built;
    }

    private static Meshes build() {
        final List<Cell> bodyCells = cells(elements(BODY));
        if (bodyCells.isEmpty()) return null;
        final List<Cell> cogCells = cells(elements(COG));

        final Source source = source(cogCells);
        float radius = 0.0F;
        for (final Cell cell : bodyCells) radius = Math.max(radius, source.distance(cell.center()));

        return new Meshes(new Part(bodyCells, source::distance), new Part(cogCells, center -> 0.0F), radius);
    }

    private record Source(float x, float y, float disc, float minZ, float maxZ) {
        private float distance(final Vector3f p) {
            final float radial = Math.max(0.0F, (float) Math.hypot(p.x - this.x, p.y - this.y) - this.disc);
            final float axial = Math.max(0.0F, Math.max(this.minZ - p.z, p.z - this.maxZ));
            return (float) Math.hypot(radial, axial);
        }
    }

    private static Source source(final List<Cell> cog) {
        if (cog.isEmpty()) return new Source(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        final Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        final Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        for (final Cell cell : cog) {
            for (final Vector3f corner : cell.corners()) {
                min.min(corner);
                max.max(corner);
            }
        }
        final float x = (min.x + max.x) * 0.5F;
        final float y = (min.y + max.y) * 0.5F;
        float disc = 0.0F;
        for (final Cell cell : cog) {
            for (final Vector3f corner : cell.corners()) {
                disc = Math.max(disc, (float) Math.hypot(corner.x - x, corner.y - y));
            }
        }
        return new Source(x, y, disc, min.z, max.z);
    }

    // --- part ---

    private record Cell(Vector3f[] corners, Vector3f center, Vector3f normal) {}

    private static final class Part {
        private final VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        private final boolean drawable;

        private Part(final List<Cell> cells, final ToDoubleFunction<Vector3f> distance) {
            this.drawable = upload(this.buffer, cells, distance);
        }

        private void draw(final ShaderInstance shader, final Matrix4f modelView, final Matrix4f projection) {
            if (!this.drawable) return;
            this.buffer.bind();
            this.buffer.drawWithShader(modelView, projection, shader);
            VertexBuffer.unbind();
        }

        private void close() {
            this.buffer.close();
        }
    }

    private static boolean upload(
            final VertexBuffer buffer,
            final List<Cell> cells,
            final ToDoubleFunction<Vector3f> distanceOf
    ) {
        if (cells.isEmpty()) return false;
        try (ByteBufferBuilder scratch = new ByteBufferBuilder(Math.max(256, cells.size() * 4 * 24))) {
            final BufferBuilder builder = new BufferBuilder(
                    scratch, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (final Cell cell : cells) {
                final float distance = (float) distanceOf.applyAsDouble(cell.center());
                final Vector3f normal = cell.normal();
                for (final Vector3f corner : cell.corners()) {
                    builder.addVertex(corner.x, corner.y, corner.z)
                            .setUv(distance, 0.0F)
                            .setColor(normal.x * 0.5F + 0.5F, normal.y * 0.5F + 0.5F, normal.z * 0.5F + 0.5F, 1.0F);
                }
            }

            final MeshData data = builder.build();
            if (data == null) return false;
            buffer.bind();
            buffer.upload(data);
            VertexBuffer.unbind();
            return true;
        }
    }

    // --- lattice ---

    private record Frame(Quaternionf rotation, Vector3f origin, Vector3f scale) {}

    private static List<BlockElement> elements(final ResourceLocation location) {
        final var resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            LOGGER.error("Missing compression gun geometry {}", location);
            return List.of();
        }
        try (Reader reader = resource.get().openAsReader()) {
            return BlockModel.fromStream(reader).getElements();
        } catch (final Exception exception) {
            LOGGER.error("Could not read compression gun geometry {}", location, exception);
            return List.of();
        }
    }

    private static List<Cell> cells(final List<BlockElement> elements) {
        final List<Cell> cells = new ArrayList<>();
        for (final BlockElement element : elements) {
            final Frame frame = frame(element);
            final float[] size = {
                    element.to.x() - element.from.x(),
                    element.to.y() - element.from.y(),
                    element.to.z() - element.from.z() };
            final int[] counts = new int[3];
            final float[] step = new float[3];
            for (int axis = 0; axis < 3; axis++) {
                counts[axis] = Math.max(1, Math.round(size[axis] / CELL));
                step[axis] = size[axis] / counts[axis];
            }

            for (final Direction face : element.faces.keySet()) {
                final int normal = face.getAxis().ordinal();
                final int a = (normal + 1) % 3;
                final int b = (normal + 2) % 3;
                final boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
                final float plane = positive ? component(element.to, normal) : component(element.from, normal);
                final float depth = positive ? plane - step[normal] * 0.5F : plane + step[normal] * 0.5F;
                final Vector3f faceNormal = frame.rotation().transform(
                        new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ())).normalize();

                for (int i = 0; i < counts[a]; i++) {
                    final float a0 = component(element.from, a) + step[a] * i;
                    final float a1 = component(element.from, a) + step[a] * (i + 1);
                    for (int j = 0; j < counts[b]; j++) {
                        final float b0 = component(element.from, b) + step[b] * j;
                        final float b1 = component(element.from, b) + step[b] * (j + 1);

                        final Vector3f p00 = place(frame, point(normal, plane, a, a0, b, b0));
                        final Vector3f p10 = place(frame, point(normal, plane, a, a1, b, b0));
                        final Vector3f p11 = place(frame, point(normal, plane, a, a1, b, b1));
                        final Vector3f p01 = place(frame, point(normal, plane, a, a0, b, b1));
                        cells.add(new Cell(
                                positive
                                        ? new Vector3f[] { p00, p10, p11, p01 }
                                        : new Vector3f[] { p00, p01, p11, p10 },
                                place(frame, point(normal, depth, a, (a0 + a1) * 0.5F, b, (b0 + b1) * 0.5F)),
                                faceNormal));
                    }
                }
            }
        }
        return cells;
    }

    private static Frame frame(final BlockElement element) {
        final BlockElementRotation rotation = element.rotation;
        if (rotation == null) return new Frame(new Quaternionf(), new Vector3f(), new Vector3f(1.0F));

        final Vector3f axis;
        final Vector3f scale;
        switch (rotation.axis()) {
            case X -> {
                axis = new Vector3f(1.0F, 0.0F, 0.0F);
                scale = new Vector3f(0.0F, 1.0F, 1.0F);
            }
            case Y -> {
                axis = new Vector3f(0.0F, 1.0F, 0.0F);
                scale = new Vector3f(1.0F, 0.0F, 1.0F);
            }
            default -> {
                axis = new Vector3f(0.0F, 0.0F, 1.0F);
                scale = new Vector3f(1.0F, 1.0F, 0.0F);
            }
        }
        if (rotation.rescale()) {
            scale.mul(Math.abs(rotation.angle()) == 22.5F ? RESCALE_22_5 : RESCALE_45).add(1.0F, 1.0F, 1.0F);
        } else {
            scale.set(1.0F);
        }
        return new Frame(
                new Quaternionf().rotationAxis(rotation.angle() * Mth.DEG_TO_RAD, axis),
                new Vector3f(rotation.origin()),
                scale);
    }

    private static Vector3f place(final Frame frame, final Vector3f modelUnits) {
        final Vector3f p = modelUnits.div(16.0F);
        p.sub(frame.origin());
        frame.rotation().transform(p);
        p.mul(frame.scale());
        p.add(frame.origin());
        return p.sub(0.5F, 0.5F, 0.5F);
    }

    private static Vector3f point(final int normal, final float n, final int a, final float av, final int b, final float bv) {
        final float[] xyz = new float[3];
        xyz[normal] = n;
        xyz[a] = av;
        xyz[b] = bv;
        return new Vector3f(xyz[0], xyz[1], xyz[2]);
    }

    private static float component(final Vector3f v, final int axis) {
        return axis == 0 ? v.x() : axis == 1 ? v.y() : v.z();
    }
}
