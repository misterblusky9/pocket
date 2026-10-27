package com.misterblusky9.pocket.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.misterblusky9.pocket.block.FacadeTiling;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.item.PocketContainer;
import com.misterblusky9.pocket.pocket.PocketCopycatPreview;
import com.misterblusky9.pocket.pocket.PocketRenderSnapshot;
import com.misterblusky9.pocket.pocket.PocketWheelPreview;
import com.misterblusky9.pocket.pocket.ShellVoxels;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.neoforged.neoforge.client.model.data.ModelData;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PocketedSubLevelItemRenderer extends CustomRenderedItemModelRenderer {
    private static final int CACHE_LIMIT = 96;
    private static final int MESH_CACHE_LIMIT = 32;
    private static final int COARSE_RESOLUTION_MIN = 6;

    private static final float SHADE_TOP = 1.00F;
    private static final float SHADE_BOTTOM = 0.55F;
    private static final float SHADE_NORTH_SOUTH = 0.80F;
    private static final float SHADE_EAST_WEST = 0.66F;

    private static final Map<UUID, PocketRenderSnapshot> CACHE = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, PocketRenderSnapshot> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    private static final Map<MeshKey, CraftMesh> MESHES = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<MeshKey, CraftMesh> eldest) {
            return size() > MESH_CACHE_LIMIT;
        }
    };

    private static final double COMPRESSED_SCALE = 1.0D / 16.0D;

    private static final float WHEEL_MIN_RADIUS = 0.35F;
    private static final float WHEEL_THICKNESS_RATIO = 0.32F;
    private static final float WHEEL_MIN_THICKNESS = 0.14F;
    private static final float WHEEL_RED = 0.13F;
    private static final float WHEEL_GREEN = 0.13F;
    private static final float WHEEL_BLUE = 0.145F;

    private enum Detail {
        FAST(3_000, 6_000, 6, 6),
        FANCY(6_000, 12_000, 32, 44),
        FABULOUS(12_000, 24_000, 44, 62);

        private final int budget;
        private final int plateBudget;
        private final int resolution;
        private final int plateResolution;

        Detail(int budget, int plateBudget, int resolution, int plateResolution) {
            this.budget = budget;
            this.plateBudget = plateBudget;
            this.resolution = resolution;
            this.plateResolution = plateResolution;
        }

        private static Detail current() {
            return switch (Minecraft.getInstance().options.graphicsMode().get()) {
                case FAST -> FAST;
                case FANCY -> FANCY;
                case FABULOUS -> FABULOUS;
            };
        }
    }

    @Override
    protected void render(
            ItemStack stack,
            CustomRenderedItemModel model,
            PartialItemModelRenderer itemRenderer,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        PocketContainer container = PocketContainer.of(stack);
        BakedModel boxModel = container == PocketContainer.CARDBOARD_BOX
                ? model.getOriginalModel()
                : Minecraft.getInstance().getItemRenderer().getItemModelShaper()
                .getItemModel(container.item());

        if (container.translucent()) {
            itemRenderer.render(boxModel, packedLight);
        } else {
            itemRenderer.renderSolid(boxModel, packedLight);
        }

        renderCraftInBox(stack, poseStack, bufferSource, packedLight, packedOverlay);
    }

    public static void renderCraftInBox(
            ItemStack stack,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        UUID token = PocketCaseItem.token(stack);
        PocketRenderSnapshot snapshot = token == null ? null : CACHE.get(token);
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (snapshot != null && customData != null
                && snapshot.revision() != PocketRenderSnapshot.revisionOf(customData.getUnsafe())) {
            snapshot = null;
        }

        if (snapshot == null) {
            snapshot = PocketCaseItem.renderSnapshot(stack);
            if (token != null && snapshot != null) {
                CACHE.put(token, snapshot);
            }
        }

        if (snapshot == null || snapshot.blockCount() == 0) {
            return;
        }

        PocketRenderSnapshot drawn = snapshot;
        double scale = PocketCaseItem.pocketedScale(stack);
        int[] anchor = drawn.gridAnchor();
        int[] shift = FacadeTiling.reduce(PocketCaseItem.facadeOffset(stack), scale);
        for (int axis = 0; axis < 3; axis++) anchor[axis] += shift[axis];
        PocketContainer container = PocketContainer.of(stack);
        boolean plate = container == PocketContainer.BRASS_DISPLAY_PLATE
                || container == PocketContainer.ANDESITE_DISPLAY_PLATE;
        MeshKey key = new MeshKey(drawn, Detail.current(), plate, scale, anchor[0], anchor[1], anchor[2]);

        CraftMesh mesh = MESHES.get(key);
        if (mesh == null) {
            CraftMesh[] built = new CraftMesh[1];
            CopycatFacadeFrames.inPreview(scale, anchor,
                    () -> built[0] = buildMesh(drawn, gridFor(drawn, key.detail(), plate)));
            mesh = built[0];
            MESHES.put(key, mesh);
        }

        drawCraft(stack, container, drawn, mesh, poseStack, bufferSource, packedLight, packedOverlay);
    }

    private static void drawCraft(
            ItemStack stack,
            PocketContainer container,
            PocketRenderSnapshot snapshot,
            CraftMesh mesh,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        poseStack.pushPose();

        poseStack.translate(container.centreX(), container.floorY(), container.centreZ());

        int quarterTurns = PocketCaseItem.modelQuarterTurns(stack);
        boolean sideways = (quarterTurns & 1) == 1;
        int footprintX = sideways ? snapshot.sizeZ() : snapshot.sizeX();
        int footprintZ = sideways ? snapshot.sizeX() : snapshot.sizeZ();
        float fit = (float) Math.min(
                Math.min(COMPRESSED_SCALE, container.clearHeight() / Math.max(1, snapshot.sizeY())),
                Math.min(container.clearWidthX() / Math.max(1, footprintX),
                        container.clearWidthZ() / Math.max(1, footprintZ)));
        poseStack.scale(fit, fit, fit);

        if (quarterTurns != 0) poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F * quarterTurns));

        poseStack.translate(-snapshot.sizeX() * 0.5D, 0.0D, -snapshot.sizeZ() * 0.5D);

        if (mesh.sizeX() != snapshot.sizeX() || mesh.sizeY() != snapshot.sizeY() || mesh.sizeZ() != snapshot.sizeZ()) {
            poseStack.scale(
                    snapshot.sizeX() / (float) mesh.sizeX(),
                    snapshot.sizeY() / (float) mesh.sizeY(),
                    snapshot.sizeZ() / (float) mesh.sizeZ());
        }

        emit(mesh, bufferSource, poseStack.last(), packedLight, packedOverlay);

        poseStack.popPose();
    }

    private static Grid gridFor(PocketRenderSnapshot snapshot, Detail detail, boolean plate) {
        int budget = plate ? detail.plateBudget : detail.budget;
        int resolution = plate ? detail.plateResolution : detail.resolution;
        int maxDim = Math.max(snapshot.sizeX(), Math.max(snapshot.sizeY(), snapshot.sizeZ()));
        Grid asIs = new Grid(
                snapshot.sizeX(), snapshot.sizeY(), snapshot.sizeZ(),
                snapshot.blocks(), snapshot.hasExactBlockGrid());

        if (snapshot.blockCount() <= budget) return asIs;

        for (; ; resolution = resolution * 3 / 4) {
            if (resolution < maxDim) {
                Grid coarse = coarsen(snapshot, maxDim, resolution);
                if (coarse.blocks().length / PocketRenderSnapshot.STRIDE <= budget
                        || resolution <= COARSE_RESOLUTION_MIN) {
                    return coarse;
                }
            } else if (resolution <= COARSE_RESOLUTION_MIN) {
                return asIs;
            }
        }
    }

    private static Grid coarsen(PocketRenderSnapshot snapshot, int maxDim, int resolution) {
        int sizeX = snapshot.sizeX();
        int sizeY = snapshot.sizeY();
        int sizeZ = snapshot.sizeZ();
        int gridX = axisCells(sizeX, maxDim, resolution);
        int gridY = axisCells(sizeY, maxDim, resolution);
        int gridZ = axisCells(sizeZ, maxDim, resolution);

        int[] data = snapshot.blocks();
        int stride = PocketRenderSnapshot.STRIDE;
        int[] cells = new int[data.length];
        int length = 0;
        IntSet taken = new IntOpenHashSet();
        for (int i = 0; i + stride - 1 < data.length; i += stride) {
            int packed = data[i];
            int cx = Math.min(gridX - 1, (int) (((long) xOf(packed) * gridX) / sizeX));
            int cy = Math.min(gridY - 1, (int) (((long) yOf(packed) * gridY) / sizeY));
            int cz = Math.min(gridZ - 1, (int) (((long) zOf(packed) * gridZ) / sizeZ));
            int cell = ShellVoxels.packPosition(cx, cy, cz);
            if (!taken.add(cell)) continue;
            cells[length++] = cell;
            cells[length++] = data[i + 1];
            cells[length++] = data[i + 2];
        }
        return new Grid(gridX, gridY, gridZ, Arrays.copyOf(cells, length), false);
    }

    private static int axisCells(int size, int maxDim, int resolution) {
        return Math.max(1, (int) Math.ceil(size * (double) resolution / maxDim));
    }

    private static CraftMesh buildMesh(PocketRenderSnapshot snapshot, Grid grid) {
        TextureAtlasSprite white = Minecraft.getInstance()
                .getModelManager()
                .getAtlas(InventoryMenu.BLOCK_ATLAS)
                .getSprite(ResourceLocation.withDefaultNamespace("block/white_concrete"));

        int[] data = grid.blocks();
        int stride = PocketRenderSnapshot.STRIDE;
        boolean exactGeometry = grid.exact();

        SnapshotBlockAndTintGetter previewLevel = exactGeometry
                ? new SnapshotBlockAndTintGetter(data, stride, snapshot.copycats())
                : null;

        IntSet fullOccluders = new IntOpenHashSet(data.length / stride * 2);
        Map<Integer, StateRenderInfo> stateInfo = new HashMap<>();

        for (int i = 0; i + stride - 1 < data.length; i += stride) {
            int packed = data[i];
            if (!exactGeometry) {
                fullOccluders.add(packed);
                continue;
            }

            StateRenderInfo info = stateInfoFor(
                    data[i + 2], xOf(packed), yOf(packed), zOf(packed), previewLevel, stateInfo
            );
            if (info.fullOccluder()) fullOccluders.add(packed);
        }

        Map<Long, FaceAppearance> appearances = new HashMap<>();
        MeshBuilder builder = new MeshBuilder();

        for (int i = 0; i + stride - 1 < data.length; i += stride) {
            int packed = data[i];
            int stateId = data[i + 2];
            int x = ShellVoxels.unpackX(packed);
            int y = ShellVoxels.unpackY(packed);
            int z = ShellVoxels.unpackZ(packed);
            int colour = data[i + 1];

            StateRenderInfo info = exactGeometry
                    ? stateInfoFor(stateId, x, y, z, previewLevel, stateInfo)
                    : StateRenderInfo.CUBE;

            ModelMesh mesh = info.mesh();
            if (info.modelDataAware() && info.model() != null && previewLevel != null) {
                try {
                    BlockState state = Block.stateById(stateId);
                    BlockPos pos = new BlockPos(x, y, z);
                    ModelData modelData = info.model().getModelData(
                            previewLevel, pos, state, previewLevel.getModelData(pos)
                    );
                    mesh = bakeModelMesh(info.model(), state, modelData);
                } catch (RuntimeException ignored) {
                    mesh = null;
                }
            }

            if (info.modelGeometry() && mesh != null && !mesh.isEmpty()) {
                modelBlock(builder, mesh, x, y, z, colour, fullOccluders);
            } else {
                cube(builder.layer(RenderType.solid()), x, y, z, colour, stateId, white, appearances, fullOccluders);
            }
        }

        if (exactGeometry) wheels(builder.layer(RenderType.solid()), snapshot, white);

        return builder.build(grid);
    }

    private static void emit(
            CraftMesh mesh,
            MultiBufferSource bufferSource,
            PoseStack.Pose pose,
            int packedLight,
            int packedOverlay
    ) {
        Matrix4f matrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        int blockLight = packedLight & 0xFFFF;
        int skyLight = (packedLight >>> 16) & 0xFFFF;

        for (MeshLayer layer : mesh.layers()) {
            VertexConsumer consumer = bufferSource.getBuffer(layer.renderType());
            float[] floats = layer.floats();
            int[] ints = layer.ints();
            for (int v = 0, f = 0, n = 0; v < layer.vertices(); v++, f += MeshBuilder.FLOATS, n += MeshBuilder.INTS) {
                matrix.transformPosition(floats[f], floats[f + 1], floats[f + 2], position);
                normalMatrix.transform(floats[f + 5], floats[f + 6], floats[f + 7], normal).normalize();
                int baked = ints[n + 1];
                int light = Math.max(blockLight, baked & 0xFFFF) | (Math.max(skyLight, (baked >>> 16) & 0xFFFF) << 16);
                consumer.addVertex(
                        position.x, position.y, position.z, ints[n],
                        floats[f + 3], floats[f + 4], packedOverlay, light,
                        normal.x, normal.y, normal.z);
            }
        }
    }

    private static void wheels(
            LayerBuilder layer,
            PocketRenderSnapshot snapshot,
            TextureAtlasSprite sprite
    ) {
        for (PocketWheelPreview.Wheel wheel : PocketWheelPreview.decode(snapshot.wheels())) {
            float centreX = wheel.x();
            float centreY = wheel.y();
            float centreZ = wheel.z();

            float radius = Math.max(WHEEL_MIN_RADIUS, wheel.radius());
            float thickness = Math.max(WHEEL_MIN_THICKNESS, radius * WHEEL_THICKNESS_RATIO);

            Direction.Axis axle = wheel.facing().getAxis();
            float extentX = axle == Direction.Axis.X ? thickness : radius;
            float extentY = axle == Direction.Axis.Y ? thickness : radius;
            float extentZ = axle == Direction.Axis.Z ? thickness : radius;

            box(
                    layer, sprite,
                    centreX - extentX, centreY - extentY, centreZ - extentZ,
                    centreX + extentX, centreY + extentY, centreZ + extentZ
            );
        }
    }

    private static void box(
            LayerBuilder layer,
            TextureAtlasSprite sprite,
            float x0, float y0, float z0,
            float x1, float y1, float z1
    ) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float r = WHEEL_RED;
        float g = WHEEL_GREEN;
        float b = WHEEL_BLUE;

        face(layer, r, g, b, SHADE_TOP, u0, u1, v0, v1,
                0.0F, 1.0F, 0.0F, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        face(layer, r, g, b, SHADE_BOTTOM, u0, u1, v0, v1,
                0.0F, -1.0F, 0.0F, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
        face(layer, r, g, b, SHADE_NORTH_SOUTH, u0, u1, v0, v1,
                0.0F, 0.0F, -1.0F, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0);
        face(layer, r, g, b, SHADE_NORTH_SOUTH, u0, u1, v0, v1,
                0.0F, 0.0F, 1.0F, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1);
        face(layer, r, g, b, SHADE_EAST_WEST, u0, u1, v0, v1,
                -1.0F, 0.0F, 0.0F, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1);
        face(layer, r, g, b, SHADE_EAST_WEST, u0, u1, v0, v1,
                1.0F, 0.0F, 0.0F, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0);
    }

    private static boolean containsAt(
            IntSet occupied,
            int x,
            int y,
            int z
    ) {
        if ((x | y | z) < 0 || x > 255 || y > 255 || z > 255) return false;
        return occupied.contains(ShellVoxels.packPosition(x, y, z));
    }

    private static int xOf(int packed) { return ShellVoxels.unpackX(packed); }
    private static int yOf(int packed) { return ShellVoxels.unpackY(packed); }
    private static int zOf(int packed) { return ShellVoxels.unpackZ(packed); }

    private static StateRenderInfo stateInfoFor(
            int stateId,
            int x,
            int y,
            int z,
            SnapshotBlockAndTintGetter previewLevel,
            Map<Integer, StateRenderInfo> cache
    ) {
        if (stateId == PocketRenderSnapshot.NO_STATE) return StateRenderInfo.CUBE;

        StateRenderInfo cached = cache.get(stateId);
        if (cached != null) return cached;

        StateRenderInfo resolved = StateRenderInfo.CUBE;
        BlockState state = Block.stateById(stateId);
        if (state != null && !state.isAir()) {
            try {
                BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
                boolean fullOccluder = state.canOcclude()
                        && state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);

                ModelData firstModelData;
                if (previewLevel == null) {
                    firstModelData = ModelData.EMPTY;
                } else {
                    BlockPos modelPos = new BlockPos(x, y, z);
                    firstModelData = model.getModelData(
                            previewLevel, modelPos, state, previewLevel.getModelData(modelPos)
                    );
                }
                boolean modelDataAware = firstModelData != null
                        && !firstModelData.getProperties().isEmpty();

                if (modelDataAware) {
                    resolved = new StateRenderInfo(true, fullOccluder, model, true, null);
                } else if (fullOccluder) {
                    resolved = new StateRenderInfo(false, true, model, false, null);
                } else {
                    ModelMesh mesh = bakeModelMesh(model, state, firstModelData);
                    resolved = mesh.isEmpty()
                            ? StateRenderInfo.CUBE
                            : new StateRenderInfo(true, false, model, false, mesh);
                }
            } catch (RuntimeException ignored) {
                resolved = StateRenderInfo.CUBE;
            }
        }

        cache.put(stateId, resolved);
        return resolved;
    }

    private static ModelMesh bakeModelMesh(
            BakedModel model,
            BlockState state,
            ModelData modelData
    ) {
        List<ModelLayer> layers = new ArrayList<>();
        RandomSource random = RandomSource.create(42L);
        ModelData safeModelData = modelData == null ? ModelData.EMPTY : modelData;

        try {
            random.setSeed(42L);
            for (RenderType renderType : model.getRenderTypes(state, random, safeModelData)) {
                ModelLayer layer = bakeModelLayer(
                        model, state, renderType, random, safeModelData
                );
                if (!layer.isEmpty()) layers.add(layer);
            }
        } catch (RuntimeException ignored) {
        }

        if (layers.isEmpty()) {
            ModelLayer fallback = bakeModelLayer(model, state, null, random, safeModelData);
            if (!fallback.isEmpty()) {
                layers.add(new ModelLayer(
                        RenderType.solid(), fallback.unculled(), fallback.faces()
                ));
            }
        }

        return new ModelMesh(List.copyOf(layers));
    }

    private static ModelLayer bakeModelLayer(
            BakedModel model,
            BlockState state,
            RenderType renderType,
            RandomSource random,
            ModelData modelData
    ) {
        random.setSeed(42L);
        List<BakedQuad> unculled = copyQuads(model.getQuads(
                state, null, random, modelData, renderType
        ));

        Map<Direction, List<BakedQuad>> faces = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            random.setSeed(42L);
            List<BakedQuad> quads = copyQuads(model.getQuads(
                    state, direction, random, modelData, renderType
            ));
            if (!quads.isEmpty()) faces.put(direction, quads);
        }

        return new ModelLayer(renderType, unculled, Map.copyOf(faces));
    }

    private static List<BakedQuad> copyQuads(List<BakedQuad> quads) {
        return quads == null || quads.isEmpty() ? List.of() : List.copyOf(quads);
    }

    private static void modelBlock(
            MeshBuilder builder,
            ModelMesh mesh,
            int x,
            int y,
            int z,
            int colour,
            IntSet fullOccluders
    ) {
        for (ModelLayer layer : mesh.layers()) {
            LayerBuilder target = builder.layer(layer.renderType());

            for (BakedQuad quad : layer.unculled()) {
                modelQuad(target, quad, x, y, z, colour);
            }

            for (Direction direction : Direction.values()) {
                if (containsAt(
                        fullOccluders,
                        x + direction.getStepX(),
                        y + direction.getStepY(),
                        z + direction.getStepZ()
                )) {
                    continue;
                }

                List<BakedQuad> faceQuads = layer.faces().get(direction);
                if (faceQuads == null) continue;
                for (BakedQuad quad : faceQuads) {
                    modelQuad(target, quad, x, y, z, colour);
                }
            }
        }
    }

    private static void modelQuad(
            LayerBuilder layer,
            BakedQuad quad,
            int x,
            int y,
            int z,
            int colour
    ) {
        float shade = quad.isShade() ? shadeFor(quad.getDirection()) : 1.0F;
        float red = (quad.isTinted() ? ((colour >> 16) & 0xFF) / 255.0F : 1.0F) * shade;
        float green = (quad.isTinted() ? ((colour >> 8) & 0xFF) / 255.0F : 1.0F) * shade;
        float blue = (quad.isTinted() ? (colour & 0xFF) / 255.0F : 1.0F) * shade;
        int argb = argb(red, green, blue);

        int[] vertices = quad.getVertices();
        float nx = quad.getDirection().getStepX();
        float ny = quad.getDirection().getStepY();
        float nz = quad.getDirection().getStepZ();
        for (int v = 0; v + 7 < vertices.length; v += 8) {
            int packedNormal = vertices[v + 7];
            byte bx = (byte) packedNormal;
            byte by = (byte) (packedNormal >> 8);
            byte bz = (byte) (packedNormal >> 16);
            if (bx != 0 || by != 0 || bz != 0) {
                nx = bx / 127.0F;
                ny = by / 127.0F;
                nz = bz / 127.0F;
            }
            layer.vertex(
                    Float.intBitsToFloat(vertices[v]) + x,
                    Float.intBitsToFloat(vertices[v + 1]) + y,
                    Float.intBitsToFloat(vertices[v + 2]) + z,
                    Float.intBitsToFloat(vertices[v + 4]),
                    Float.intBitsToFloat(vertices[v + 5]),
                    nx, ny, nz, argb, vertices[v + 6]);
        }
    }

    private static int argb(float red, float green, float blue) {
        return FastColor.ARGB32.color(255, (int) (red * 255.0F), (int) (green * 255.0F), (int) (blue * 255.0F));
    }

    private static float shadeFor(Direction direction) {
        return switch (direction) {
            case UP -> SHADE_TOP;
            case DOWN -> SHADE_BOTTOM;
            case NORTH, SOUTH -> SHADE_NORTH_SOUTH;
            case EAST, WEST -> SHADE_EAST_WEST;
        };
    }

    private static FaceAppearance appearanceFor(
            int stateId,
            Direction direction,
            TextureAtlasSprite fallback,
            Map<Long, FaceAppearance> cache
    ) {
        if (stateId == PocketRenderSnapshot.NO_STATE) return new FaceAppearance(fallback, true);
        long key = ((long) stateId << 3) | direction.ordinal();
        FaceAppearance cached = cache.get(key);
        if (cached != null) return cached;

        FaceAppearance resolved = new FaceAppearance(fallback, true);
        BlockState state = Block.stateById(stateId);
        if (state != null && !state.isAir()) {
            try {
                BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
                RandomSource random = RandomSource.create(42L);
                List<BakedQuad> quads = model.getQuads(
                        state, direction, random, ModelData.EMPTY, null);
                if (quads.isEmpty()) {
                    random.setSeed(42L);
                    quads = model.getQuads(state, null, random, ModelData.EMPTY, null);
                }
                BakedQuad selected = null;
                for (BakedQuad quad : quads) {
                    if (quad.getDirection() == direction) {
                        selected = quad;
                        break;
                    }
                    if (selected == null) selected = quad;
                }
                if (selected != null && selected.getSprite() != null) {
                    resolved = new FaceAppearance(selected.getSprite(), selected.isTinted());
                }
            } catch (RuntimeException ignored) {
            }
        }
        cache.put(key, resolved);
        return resolved;
    }

    private static void cube(
            LayerBuilder layer,
            int x,
            int y,
            int z,
            int colour,
            int stateId,
            TextureAtlasSprite fallback,
            Map<Long, FaceAppearance> appearances,
            IntSet fullOccluders
    ) {
        float x0 = x;
        float y0 = y;
        float z0 = z;
        float x1 = x + 1.0F;
        float y1 = y + 1.0F;
        float z1 = z + 1.0F;

        if (!containsAt(fullOccluders, x, y + 1, z)) {
            texturedFace(layer, colour, appearanceFor(stateId, Direction.UP, fallback, appearances), SHADE_TOP, 0.0F, 1.0F, 0.0F,
                    x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        }
        if (!containsAt(fullOccluders, x, y - 1, z)) {
            texturedFace(layer, colour, appearanceFor(stateId, Direction.DOWN, fallback, appearances), SHADE_BOTTOM, 0.0F, -1.0F, 0.0F,
                    x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
        }
        if (!containsAt(fullOccluders, x, y, z - 1)) {
            texturedFace(layer, colour, appearanceFor(stateId, Direction.NORTH, fallback, appearances), SHADE_NORTH_SOUTH, 0.0F, 0.0F, -1.0F,
                    x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0);
        }
        if (!containsAt(fullOccluders, x, y, z + 1)) {
            texturedFace(layer, colour, appearanceFor(stateId, Direction.SOUTH, fallback, appearances), SHADE_NORTH_SOUTH, 0.0F, 0.0F, 1.0F,
                    x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1);
        }
        if (!containsAt(fullOccluders, x - 1, y, z)) {
            texturedFace(layer, colour, appearanceFor(stateId, Direction.WEST, fallback, appearances), SHADE_EAST_WEST, -1.0F, 0.0F, 0.0F,
                    x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1);
        }
        if (!containsAt(fullOccluders, x + 1, y, z)) {
            texturedFace(layer, colour, appearanceFor(stateId, Direction.EAST, fallback, appearances), SHADE_EAST_WEST, 1.0F, 0.0F, 0.0F,
                    x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0);
        }
    }

    private static void texturedFace(
            LayerBuilder layer,
            int colour,
            FaceAppearance appearance,
            float shade,
            float nx, float ny, float nz,
            float ax, float ay, float az,
            float bx, float by, float bz,
            float cx, float cy, float cz,
            float dx, float dy, float dz
    ) {
        float red = appearance.tint() ? ((colour >> 16) & 0xFF) / 255.0F : 1.0F;
        float green = appearance.tint() ? ((colour >> 8) & 0xFF) / 255.0F : 1.0F;
        float blue = appearance.tint() ? (colour & 0xFF) / 255.0F : 1.0F;
        TextureAtlasSprite sprite = appearance.sprite();
        face(layer, red, green, blue, shade,
                sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV1(), nx, ny, nz,
                ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz);
    }

    private static void face(
            LayerBuilder layer,
            float red,
            float green,
            float blue,
            float shade,
            float u0, float u1,
            float v0, float v1,
            float nx, float ny, float nz,
            float ax, float ay, float az,
            float bx, float by, float bz,
            float cx, float cy, float cz,
            float dx, float dy, float dz
    ) {
        int argb = argb(red * shade, green * shade, blue * shade);

        layer.vertex(ax, ay, az, u0, v0, nx, ny, nz, argb, 0);
        layer.vertex(bx, by, bz, u0, v1, nx, ny, nz, argb, 0);
        layer.vertex(cx, cy, cz, u1, v1, nx, ny, nz, argb, 0);
        layer.vertex(dx, dy, dz, u1, v0, nx, ny, nz, argb, 0);
    }

    private static final class MeshBuilder {
        private static final int FLOATS = 8;
        private static final int INTS = 2;

        private final Map<RenderType, LayerBuilder> layers = new LinkedHashMap<>();

        private LayerBuilder layer(RenderType renderType) {
            return this.layers.computeIfAbsent(renderType, type -> new LayerBuilder());
        }

        private CraftMesh build(Grid grid) {
            List<MeshLayer> built = new ArrayList<>(this.layers.size());
            this.layers.forEach((renderType, layer) -> {
                if (layer.vertices == 0) return;
                built.add(new MeshLayer(
                        renderType,
                        Arrays.copyOf(layer.floats, layer.vertices * FLOATS),
                        Arrays.copyOf(layer.ints, layer.vertices * INTS),
                        layer.vertices));
            });
            return new CraftMesh(grid.sizeX(), grid.sizeY(), grid.sizeZ(), List.copyOf(built));
        }
    }

    private static final class LayerBuilder {
        private float[] floats = new float[MeshBuilder.FLOATS * 1024];
        private int[] ints = new int[MeshBuilder.INTS * 1024];
        private int vertices;

        private void vertex(
                float x, float y, float z,
                float u, float v,
                float nx, float ny, float nz,
                int argb,
                int bakedLight
        ) {
            if ((this.vertices + 1) * MeshBuilder.FLOATS > this.floats.length) {
                this.floats = Arrays.copyOf(this.floats, this.floats.length * 2);
                this.ints = Arrays.copyOf(this.ints, this.ints.length * 2);
            }
            int f = this.vertices * MeshBuilder.FLOATS;
            this.floats[f] = x;
            this.floats[f + 1] = y;
            this.floats[f + 2] = z;
            this.floats[f + 3] = u;
            this.floats[f + 4] = v;
            this.floats[f + 5] = nx;
            this.floats[f + 6] = ny;
            this.floats[f + 7] = nz;
            int n = this.vertices * MeshBuilder.INTS;
            this.ints[n] = argb;
            this.ints[n + 1] = bakedLight;
            this.vertices++;
        }
    }

    private static final class SnapshotBlockAndTintGetter implements BlockAndTintGetter {
        private final Int2ObjectOpenHashMap<BlockState> states = new Int2ObjectOpenHashMap<>();
        private final Int2ObjectOpenHashMap<BlockEntity> blockEntities = new Int2ObjectOpenHashMap<>();
        private final Int2ObjectOpenHashMap<ModelData> modelData = new Int2ObjectOpenHashMap<>();
        private final BlockAndTintGetter delegate;

        private SnapshotBlockAndTintGetter(
                int[] data,
                int stride,
                net.minecraft.nbt.ListTag copycats
        ) {
            var clientLevel = Minecraft.getInstance().level;
            this.delegate = clientLevel;

            for (int i = 0; i + stride - 1 < data.length; i += stride) {
                int stateId = data[i + 2];
                if (stateId == PocketRenderSnapshot.NO_STATE) continue;

                BlockState state = Block.stateById(stateId);
                if (state != null && !state.isAir()) {
                    this.states.put(data[i], state);
                }
            }

            if (this.delegate == null || copycats == null || copycats.isEmpty()) return;

            for (PocketCopycatPreview.PackedEntry entry : PocketCopycatPreview.decode(copycats)) {
                int packed = entry.packedPos();
                BlockState state = this.states.get(packed);
                if (state == null || !(state.getBlock() instanceof EntityBlock entityBlock)) continue;

                BlockPos pos = new BlockPos(xOf(packed), yOf(packed), zOf(packed));
                try {
                    BlockEntity blockEntity = entityBlock.newBlockEntity(pos, state);
                    if (!(blockEntity instanceof SmartBlockEntity smart)) continue;

                    blockEntity.setLevel(clientLevel);
                    smart.markVirtual();
                    smart.readClient(entry.data().copy(), clientLevel.registryAccess());

                    this.blockEntities.put(packed, blockEntity);
                    ModelData dataAtPos = blockEntity.getModelData();
                    this.modelData.put(packed, dataAtPos == null ? ModelData.EMPTY : dataAtPos);
                } catch (RuntimeException ignored) {
                }
            }
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            if ((x | y | z) < 0 || x > 255 || y > 255 || z > 255) {
                return Blocks.AIR.defaultBlockState();
            }
            return this.states.getOrDefault(
                    ShellVoxels.packPosition(x, y, z),
                    Blocks.AIR.defaultBlockState()
            );
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            if ((x | y | z) < 0 || x > 255 || y > 255 || z > 255) return null;
            return this.blockEntities.get(ShellVoxels.packPosition(x, y, z));
        }

        @Override
        public ModelData getModelData(BlockPos pos) {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            if ((x | y | z) < 0 || x > 255 || y > 255 || z > 255) return ModelData.EMPTY;
            return this.modelData.getOrDefault(
                    ShellVoxels.packPosition(x, y, z),
                    ModelData.EMPTY
            );
        }

        @Override
        public int getHeight() {
            return this.delegate == null ? 384 : this.delegate.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return this.delegate == null ? -64 : this.delegate.getMinBuildHeight();
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            if (this.delegate != null) return this.delegate.getShade(direction, shade);
            if (!shade) return 1.0F;
            return shadeFor(direction);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            if (this.delegate == null) {
                throw new IllegalStateException("No client level available for preview lighting");
            }
            return this.delegate.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
            return this.delegate == null ? 0xFFFFFF : this.delegate.getBlockTint(pos, colorResolver);
        }
    }

    private record MeshKey(
            PocketRenderSnapshot snapshot,
            Detail detail,
            boolean plate,
            double scale,
            int anchorX,
            int anchorY,
            int anchorZ
    ) {}

    private record Grid(int sizeX, int sizeY, int sizeZ, int[] blocks, boolean exact) {}

    private record CraftMesh(int sizeX, int sizeY, int sizeZ, List<MeshLayer> layers) {}

    private record MeshLayer(RenderType renderType, float[] floats, int[] ints, int vertices) {}

    private record StateRenderInfo(
            boolean modelGeometry,
            boolean fullOccluder,
            BakedModel model,
            boolean modelDataAware,
            ModelMesh mesh
    ) {
        private static final StateRenderInfo CUBE = new StateRenderInfo(false, true, null, false, null);
    }

    private record ModelMesh(List<ModelLayer> layers) {
        private boolean isEmpty() {
            return this.layers.isEmpty();
        }
    }

    private record ModelLayer(
            RenderType renderType,
            List<BakedQuad> unculled,
            Map<Direction, List<BakedQuad>> faces
    ) {
        private boolean isEmpty() {
            if (!this.unculled.isEmpty()) return false;
            for (List<BakedQuad> quads : this.faces.values()) {
                if (!quads.isEmpty()) return false;
            }
            return true;
        }
    }

    private record FaceAppearance(TextureAtlasSprite sprite, boolean tint) {}
}
