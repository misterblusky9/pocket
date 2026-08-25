package com.misterblusky9.pocket.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class CompressionFieldMesh implements AutoCloseable {
    public record Face(
            Direction dir,
            double plane,
            double minU,
            double maxU,
            double minV,
            double maxV,
            float distance
    ) {}

    private final DirectionBuffer[] buffers = new DirectionBuffer[Direction.values().length];
    private final int quadCount;

    private CompressionFieldMesh(final DirectionBuffer[] built, final int quadCount) {
        System.arraycopy(built, 0, this.buffers, 0, built.length);
        this.quadCount = quadCount;
    }

    public int quadCount() {
        return this.quadCount;
    }

    public static CompressionFieldMesh upload(final List<Face> faces) {
        if (faces == null || faces.isEmpty()) return null;

        final EnumMap<Direction, List<Face>> byDirection = new EnumMap<>(Direction.class);
        for (final Direction direction : Direction.values()) {
            byDirection.put(direction, new ArrayList<>());
        }
        for (final Face face : faces) {
            if (face != null && face.dir() != null) byDirection.get(face.dir()).add(face);
        }

        final DirectionBuffer[] built = new DirectionBuffer[Direction.values().length];
        int quads = 0;
        try {
            for (final Direction direction : Direction.values()) {
                final List<Face> directional = byDirection.get(direction);
                if (directional == null || directional.isEmpty()) continue;
                final DirectionBuffer part = uploadDirection(direction, directional);
                if (part == null) continue;
                built[direction.ordinal()] = part;
                quads += part.quadCount;
            }
        } catch (final RuntimeException ex) {
            closeAll(built);
            throw ex;
        }

        if (quads == 0) {
            closeAll(built);
            return null;
        }
        return new CompressionFieldMesh(built, quads);
    }

    private static DirectionBuffer uploadDirection(final Direction direction, final List<Face> faces) {
        try (ByteBufferBuilder scratch = new ByteBufferBuilder(Math.max(256, faces.size() * 4 * 24))) {
            final BufferBuilder builder = new BufferBuilder(
                    scratch, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

            double minPlane = Double.POSITIVE_INFINITY;
            double maxPlane = Double.NEGATIVE_INFINITY;
            for (final Face face : faces) {
                emit(builder, face);
                minPlane = Math.min(minPlane, face.plane());
                maxPlane = Math.max(maxPlane, face.plane());
            }

            final MeshData mesh = builder.build();
            if (mesh == null) return null;

            final VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(mesh);
            VertexBuffer.unbind();
            return new DirectionBuffer(direction, buffer, faces.size(), minPlane, maxPlane);
        }
    }

    public void draw(
            final ShaderInstance shader,
            final Matrix4f modelView,
            final Matrix4f projection,
            final Vector3f localCamera,
            final float faceOffset,
            final float frontDistance,
            final float frontWidth,
            final Vector3f pulseOrigin,
            final float pulseRadius,
            final float pulseWidth,
            final float pulseCell,
            final float strain,
            final float[] sheenColour,
            final float[] frontColour
    ) {
        if (shader == null) return;

        setUniform(shader, "FaceOffset", faceOffset);
        setUniform(shader, "FrontDistance", frontDistance);
        setUniform(shader, "FrontWidth", frontWidth);
        setUniform(shader, "PulseRadius", pulseRadius);
        setUniform(shader, "PulseWidth", pulseWidth);
        setUniform(shader, "PulseCell", pulseCell);
        setUniform(shader, "Strain", strain);

        final var originUniform = shader.getUniform("PulseOrigin");
        if (originUniform != null) originUniform.set(pulseOrigin.x, pulseOrigin.y, pulseOrigin.z);
        final var sheen = shader.getUniform("SheenColor");
        if (sheen != null) sheen.set(sheenColour[0], sheenColour[1], sheenColour[2], sheenColour[3]);
        final var front = shader.getUniform("FrontColor");
        if (front != null) front.set(frontColour[0], frontColour[1], frontColour[2], frontColour[3]);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.polygonOffset(-1.0F, -10.0F);
        RenderSystem.enablePolygonOffset();

        for (final DirectionBuffer part : this.buffers) {
            if (part == null || !part.mayFace(localCamera)) continue;
            part.buffer.bind();
            part.buffer.drawWithShader(modelView, projection, shader);
        }
        VertexBuffer.unbind();

        RenderSystem.disablePolygonOffset();
        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void setUniform(final ShaderInstance shader, final String name, final float value) {
        final var uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(value);
    }

    @Override
    public void close() {
        closeAll(this.buffers);
    }

    private static void closeAll(final DirectionBuffer[] buffers) {
        if (buffers == null) return;
        for (int i = 0; i < buffers.length; i++) {
            final DirectionBuffer part = buffers[i];
            if (part == null) continue;
            part.buffer.close();
            buffers[i] = null;
        }
    }

    private static void emit(final BufferBuilder builder, final Face face) {
        final Direction dir = face.dir();
        final Vector3f normal = new Vector3f(dir.getStepX(), dir.getStepY(), dir.getStepZ());

        final float p = (float) face.plane();
        final float u0 = (float) face.minU();
        final float u1 = (float) face.maxU();
        final float v0 = (float) face.minV();
        final float v1 = (float) face.maxV();
        final float d = face.distance();

        switch (dir) {
            case DOWN -> {
                vertex(builder, u0, p, v0, d, normal);
                vertex(builder, u1, p, v0, d, normal);
                vertex(builder, u1, p, v1, d, normal);
                vertex(builder, u0, p, v1, d, normal);
            }
            case UP -> {
                vertex(builder, u0, p, v1, d, normal);
                vertex(builder, u1, p, v1, d, normal);
                vertex(builder, u1, p, v0, d, normal);
                vertex(builder, u0, p, v0, d, normal);
            }
            case NORTH -> {
                vertex(builder, u1, v0, p, d, normal);
                vertex(builder, u0, v0, p, d, normal);
                vertex(builder, u0, v1, p, d, normal);
                vertex(builder, u1, v1, p, d, normal);
            }
            case SOUTH -> {
                vertex(builder, u0, v0, p, d, normal);
                vertex(builder, u1, v0, p, d, normal);
                vertex(builder, u1, v1, p, d, normal);
                vertex(builder, u0, v1, p, d, normal);
            }
            case WEST -> {
                vertex(builder, p, v0, u0, d, normal);
                vertex(builder, p, v0, u1, d, normal);
                vertex(builder, p, v1, u1, d, normal);
                vertex(builder, p, v1, u0, d, normal);
            }
            case EAST -> {
                vertex(builder, p, v0, u1, d, normal);
                vertex(builder, p, v0, u0, d, normal);
                vertex(builder, p, v1, u0, d, normal);
                vertex(builder, p, v1, u1, d, normal);
            }
        }
    }

    private static void vertex(
            final BufferBuilder builder,
            final float x,
            final float y,
            final float z,
            final float distance,
            final Vector3f normal
    ) {
        builder.addVertex(x, y, z)
                .setUv(distance, 0.0F)
                .setColor(
                        normal.x * 0.5F + 0.5F,
                        normal.y * 0.5F + 0.5F,
                        normal.z * 0.5F + 0.5F,
                        1.0F);
    }

    private static final class DirectionBuffer {
        private static final float CAMERA_EPSILON = 1.0E-4F;

        private final Direction direction;
        private final VertexBuffer buffer;
        private final int quadCount;
        private final double minPlane;
        private final double maxPlane;

        private DirectionBuffer(
                final Direction direction,
                final VertexBuffer buffer,
                final int quadCount,
                final double minPlane,
                final double maxPlane
        ) {
            this.direction = direction;
            this.buffer = buffer;
            this.quadCount = quadCount;
            this.minPlane = minPlane;
            this.maxPlane = maxPlane;
        }

        private boolean mayFace(final Vector3f camera) {
            if (camera == null) return true;
            return switch (this.direction) {
                case EAST -> camera.x > this.minPlane - CAMERA_EPSILON;
                case WEST -> camera.x < this.maxPlane + CAMERA_EPSILON;
                case UP -> camera.y > this.minPlane - CAMERA_EPSILON;
                case DOWN -> camera.y < this.maxPlane + CAMERA_EPSILON;
                case SOUTH -> camera.z > this.minPlane - CAMERA_EPSILON;
                case NORTH -> camera.z < this.maxPlane + CAMERA_EPSILON;
            };
        }
    }
}
