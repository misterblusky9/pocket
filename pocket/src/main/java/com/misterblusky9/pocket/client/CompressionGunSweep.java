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
                protected boolean removeEldestEntry(Map.Entry<BakedModel, Meshes> eldest) {
                    if (size() <= MAX_MESHES) return false;
                    if (eldest.getValue() != null) eldest.getValue().close();
                    return true;
                }
            };

    private CompressionGunSweep() {}

    private static ResourceLocation geometry(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                PocketSized.MOD_ID, "models/item/compression_gun/" + name + ".json");
    }

    static float advanceSource(float front, boolean spooling, float ticksUsing, float delta) {
        if (!spooling) return Math.max(0.0F, front - delta * FRONT_WIDTH / RETRACT_TICKS);
        if (ticksUsing <= CompressionGunItem.SOURCE_TICKS) return 0.0F;
        return Math.min(FRONT_WIDTH,
                (ticksUsing - CompressionGunItem.SOURCE_TICKS) * FRONT_WIDTH / CompressionGunItem.SOURCE_IGNITE_TICKS);
    }

    static float advance(
            BakedModel body,
            float front,
            boolean spooling,
            float ticksUsing,
            float delta
    ) {
        if (!spooling && front <= 0.0F) return 0.0F;
        Meshes meshes = meshes(body);
        if (meshes == null) return 0.0F;

        float full = meshes.radius() + FRONT_WIDTH;
        if (!spooling) return Math.max(0.0F, front - delta * full / RETRACT_TICKS);
        if (ticksUsing <= CompressionGunItem.SWEEP_START_TICKS) return 0.0F;
        return Math.min(full, CompressionGunItem.sweepProgress(ticksUsing) * meshes.radius());
    }

    static void render(
            BakedModel body,
            float source,
            float front,
            boolean growing,
            ItemDisplayContext transformType,
            float renderTicks,
            PoseStack ms,
            MultiBufferSource buffers,
            Consumer<PoseStack> spinCog
    ) {
        if (!(source > 0.0F || front > 0.0F) || !inHand(transformType)) return;

        ShaderInstance shader = PocketShaders.compressionField();
        if (shader == null) return;

        Meshes meshes = meshes(body);
        if (meshes == null) return;

        if (buffers instanceof MultiBufferSource.BufferSource batched) batched.endBatch();

        float[] sheenColour = growing ? GROW_SHEEN : SHRINK_SHEEN;
        float[] frontColour = growing ? GROW_FRONT : SHRINK_FRONT;

        set(shader, "FaceOffset", FACE_OFFSET);
        set(shader, "FrontWidth", FRONT_WIDTH);
        set(shader, "PulseRadius", -1000.0F);
        set(shader, "PulseWidth", 1.0F);
        set(shader, "PulseCell", 1.0F);
        set(shader, "Strain", strain(front >= meshes.radius(), renderTicks));
        set(shader, "ShimmerTime", renderTicks);
        set(shader, "Halted", 0.0F);

        var grid = shader.getUniform("GridOrigin");
        if (grid != null) grid.set(0.0F, 0.0F, 0.0F);
        var pulse = shader.getUniform("PulseOrigin");
        if (pulse != null) pulse.set(0.0F, 0.0F, 0.0F);
        var sheen = shader.getUniform("SheenColor");
        if (sheen != null) sheen.set(sheenColour[0], sheenColour[1], sheenColour[2], sheenColour[3]);
        var frontUniform = shader.getUniform("FrontColor");
        if (frontUniform != null) frontUniform.set(frontColour[0], frontColour[1], frontColour[2], frontColour[3]);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.polygonOffset(-1.0F, -10.0F);
        RenderSystem.enablePolygonOffset();

        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(ms.last().pose());
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

    private static boolean inHand(ItemDisplayContext transformType) {
        return transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    private static float strain(boolean fired, float renderTicks) {
        if (!fired) return 0.0F;
        float grind = 0.5F + 0.5F * Mth.sin(renderTicks * GRIND_FAST * 1.3F);
        float shudder = 0.5F + 0.5F * Mth.sin(renderTicks * GRIND_SLOW * 2.7F + 0.9F);
        return SEALED_STRAIN * (0.25F + 0.75F * grind * grind * shudder);
    }

    private static void set(ShaderInstance shader, String name, float value) {
        var uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(value);
    }

    private static Meshes meshes(BakedModel body) {
        if (MESHES.containsKey(body)) return MESHES.get(body);

        Meshes built = build();
        MESHES.put(body, built);
        return built;
    }

    private static Meshes build() {
        List<Cell> bodyCells = cells(elements(BODY));
        if (bodyCells.isEmpty()) return null;
        List<Cell> cogCells = cells(elements(COG));

        Source source = source(cogCells);
        float radius = 0.0F;
        for (Cell cell : bodyCells) radius = Math.max(radius, source.distance(cell.center()));

        return new Meshes(new Part(bodyCells, source::distance), new Part(cogCells, center -> 0.0F), radius);
    }

    private record Source(float x, float y, float disc, float minZ, float maxZ) {
        private float distance(Vector3f p) {
            float radial = Math.max(0.0F, (float) Math.hypot(p.x - this.x, p.y - this.y) - this.disc);
            float axial = Math.max(0.0F, Math.max(this.minZ - p.z, p.z - this.maxZ));
            return (float) Math.hypot(radial, axial);
        }
    }

    private static Source source(List<Cell> cog) {
        if (cog.isEmpty()) return new Source(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        for (Cell cell : cog) {
            for (Vector3f corner : cell.corners()) {
                min.min(corner);
                max.max(corner);
            }
        }
        float x = (min.x + max.x) * 0.5F;
        float y = (min.y + max.y) * 0.5F;
        float disc = 0.0F;
        for (Cell cell : cog) {
            for (Vector3f corner : cell.corners()) {
                disc = Math.max(disc, (float) Math.hypot(corner.x - x, corner.y - y));
            }
        }
        return new Source(x, y, disc, min.z, max.z);
    }


    private record Cell(Vector3f[] corners, Vector3f center, Vector3f normal) {}

    private static final class Part {
        private final VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        private final boolean drawable;

        private Part(List<Cell> cells, ToDoubleFunction<Vector3f> distance) {
            this.drawable = upload(this.buffer, cells, distance);
        }

        private void draw(ShaderInstance shader, Matrix4f modelView, Matrix4f projection) {
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
            VertexBuffer buffer,
            List<Cell> cells,
            ToDoubleFunction<Vector3f> distanceOf
    ) {
        if (cells.isEmpty()) return false;
        try (ByteBufferBuilder scratch = new ByteBufferBuilder(Math.max(256, cells.size() * 4 * 24))) {
            BufferBuilder builder = new BufferBuilder(
                    scratch, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (Cell cell : cells) {
                float distance = (float) distanceOf.applyAsDouble(cell.center());
                Vector3f normal = cell.normal();
                for (Vector3f corner : cell.corners()) {
                    builder.addVertex(corner.x, corner.y, corner.z)
                            .setUv(distance, 0.0F)
                            .setColor(normal.x * 0.5F + 0.5F, normal.y * 0.5F + 0.5F, normal.z * 0.5F + 0.5F, 1.0F);
                }
            }

            MeshData data = builder.build();
            if (data == null) return false;
            buffer.bind();
            buffer.upload(data);
            VertexBuffer.unbind();
            return true;
        }
    }


    private record Frame(Quaternionf rotation, Vector3f origin, Vector3f scale) {}

    private static List<BlockElement> elements(ResourceLocation location) {
        var resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            LOGGER.error("Missing compression gun geometry {}", location);
            return List.of();
        }
        try (Reader reader = resource.get().openAsReader()) {
            return BlockModel.fromStream(reader).getElements();
        } catch (Exception exception) {
            LOGGER.error("Could not read compression gun geometry {}", location, exception);
            return List.of();
        }
    }

    private static List<Cell> cells(List<BlockElement> elements) {
        List<Cell> cells = new ArrayList<>();
        for (BlockElement element : elements) {
            Frame frame = frame(element);
            float[] size = {
                    element.to.x() - element.from.x(),
                    element.to.y() - element.from.y(),
                    element.to.z() - element.from.z() };
            int[] counts = new int[3];
            float[] step = new float[3];
            for (int axis = 0; axis < 3; axis++) {
                counts[axis] = Math.max(1, Math.round(size[axis] / CELL));
                step[axis] = size[axis] / counts[axis];
            }

            for (Direction face : element.faces.keySet()) {
                int normal = face.getAxis().ordinal();
                int a = (normal + 1) % 3;
                int b = (normal + 2) % 3;
                boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
                float plane = positive ? component(element.to, normal) : component(element.from, normal);
                float depth = positive ? plane - step[normal] * 0.5F : plane + step[normal] * 0.5F;
                Vector3f faceNormal = frame.rotation().transform(
                        new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ())).normalize();

                for (int i = 0; i < counts[a]; i++) {
                    float a0 = component(element.from, a) + step[a] * i;
                    float a1 = component(element.from, a) + step[a] * (i + 1);
                    for (int j = 0; j < counts[b]; j++) {
                        float b0 = component(element.from, b) + step[b] * j;
                        float b1 = component(element.from, b) + step[b] * (j + 1);

                        Vector3f p00 = place(frame, point(normal, plane, a, a0, b, b0));
                        Vector3f p10 = place(frame, point(normal, plane, a, a1, b, b0));
                        Vector3f p11 = place(frame, point(normal, plane, a, a1, b, b1));
                        Vector3f p01 = place(frame, point(normal, plane, a, a0, b, b1));
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

    private static Frame frame(BlockElement element) {
        BlockElementRotation rotation = element.rotation;
        if (rotation == null) return new Frame(new Quaternionf(), new Vector3f(), new Vector3f(1.0F));

        Vector3f axis;
        Vector3f scale;
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

    private static Vector3f place(Frame frame, Vector3f modelUnits) {
        Vector3f p = modelUnits.div(16.0F);
        p.sub(frame.origin());
        frame.rotation().transform(p);
        p.mul(frame.scale());
        p.add(frame.origin());
        return p.sub(0.5F, 0.5F, 0.5F);
    }

    private static Vector3f point(int normal, float n, int a, float av, int b, float bv) {
        float[] xyz = new float[3];
        xyz[normal] = n;
        xyz[a] = av;
        xyz[b] = bv;
        return new Vector3f(xyz[0], xyz[1], xyz[2]);
    }

    private static float component(Vector3f v, int axis) {
        return axis == 0 ? v.x() : axis == 1 ? v.y() : v.z();
    }
}
