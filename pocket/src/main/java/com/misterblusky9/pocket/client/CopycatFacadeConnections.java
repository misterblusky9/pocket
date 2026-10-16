package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.FacadeTiling;
import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import com.simibubi.create.content.decoration.copycat.CopycatModel;
import com.simibubi.create.foundation.block.connected.CTModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CopycatFacadeConnections {
    private static final int REGION_REACH = 16;
    private static final int MAX_TILES = FacadeTiling.MAX_TILES_PER_AXIS * FacadeTiling.MAX_TILES_PER_AXIS;
    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    private static final long PINNED_SEED = 42L;
    private static final Map<CellKey, List<BakedQuad>> CELLS = new ConcurrentHashMap<>();

    public record Faces(Face[] faces) {
        public int mask(Direction face, int tileA, int tileB) {
            Face entry = this.faces[face.get3DDataValue()];
            return entry == null ? -1 : entry.mask(tileA, tileB);
        }
    }

    private record Face(int tileA, int tileB, int countA, int countB, short[] masks) {
        private int mask(int a, int b) {
            int i = a - this.tileA, j = b - this.tileB;
            if (i < 0 || j < 0 || i >= this.countA || j >= this.countB) return -1;
            return this.masks[i * this.countB + j];
        }
    }

    private record CellKey(BlockState material, Direction face, Direction side, RenderType renderType, int mask) {}

    public static boolean connects(BlockState material) {
        return CopycatModel.getModelOf(material) instanceof CTModel;
    }

    public static Faces gather(
            BlockAndTintGetter world,
            BlockPos pos,
            CopycatFacadeFrames.Frame frame,
            BlockState material
    ) {
        Face[] faces = new Face[6];
        for (Direction face : Direction.values()) {
            Direction.Axis axisA = axisA(face), axisB = axisB(face);
            int blockA = frame.along(axisA), blockB = frame.along(axisB);
            int[] rangeA = FacadeTiling.tileRange(blockA, frame.scale());
            int[] rangeB = FacadeTiling.tileRange(blockB, frame.scale());
            int countA = rangeA[1] - rangeA[0] + 1, countB = rangeB[1] - rangeB[0] + 1;
            if (countA <= 0 || countB <= 0 || countA * countB > MAX_TILES) continue;

            Map<Long, Boolean> probed = new HashMap<>();
            short[] masks = new short[countA * countB];
            for (int i = 0; i < countA; i++) {
                int[] acrossA = FacadeTiling.blocksAcross(rangeA[0] + i, frame.scale());
                for (int j = 0; j < countB; j++) {
                    int[] acrossB = FacadeTiling.blocksAcross(rangeB[0] + j, frame.scale());
                    int mask = 0;
                    for (int dA = -1; dA <= 1; dA++) {
                        for (int dB = -1; dB <= 1; dB++) {
                            if (dA == 0 && dB == 0) continue;
                            int offsetA = dA == 0 ? 0 : (dA < 0 ? acrossA[0] : acrossA[1]) - blockA;
                            int offsetB = dB == 0 ? 0 : (dB < 0 ? acrossB[0] : acrossB[1]) - blockB;
                            boolean connected = offsetA == 0 && offsetB == 0
                                    || probed.computeIfAbsent(((long) offsetA << 32) ^ (offsetB & 0xFFFFFFFFL),
                                            ignored -> probe(world, pos, face, axisA, axisB, offsetA, offsetB, material));
                            if (connected) mask |= bit(dA, dB);
                        }
                    }
                    masks[i * countB + j] = (short) mask;
                }
            }
            faces[face.get3DDataValue()] = new Face(rangeA[0], rangeB[0], countA, countB, masks);
        }
        return new Faces(faces);
    }

    public static List<BakedQuad> cell(
            BlockState material,
            Direction face,
            Direction side,
            RenderType renderType,
            int mask
    ) {
        return CELLS.computeIfAbsent(new CellKey(material, face, side, renderType, mask), key -> {
            var model = CopycatModel.getModelOf(material);
            ModelData data = model.getModelData(new Neighbourhood(material, face, mask), ORIGIN, material, ModelData.EMPTY);
            return List.copyOf(model.getQuads(material, side, RandomSource.create(PINNED_SEED), data, renderType));
        });
    }

    public static void clear() {
        CELLS.clear();
    }

    public static Direction.Axis axisA(Direction face) {
        return face.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    public static Direction.Axis axisB(Direction face) {
        return face.getAxis() == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;
    }

    private static int bit(int dA, int dB) {
        return 1 << ((dA + 1) * 3 + (dB + 1));
    }

    private static boolean probe(
            BlockAndTintGetter world,
            BlockPos pos,
            Direction face,
            Direction.Axis axisA,
            Direction.Axis axisB,
            int offsetA,
            int offsetB,
            BlockState material
    ) {
        BlockGetter reader = Math.max(Math.abs(offsetA), Math.abs(offsetB)) <= REGION_REACH
                || CopycatFacadeFrames.previewing()
                || CopycatFacadeFrames.contraptionWorld(world) != null
                || Minecraft.getInstance().level == null
                ? world
                : Minecraft.getInstance().level;
        BlockPos neighbour = pos.relative(axisA, offsetA).relative(axisB, offsetB);
        try {
            if (appearance(reader, neighbour).getBlock() != material.getBlock()) return false;
            BlockPos front = neighbour.relative(face);
            BlockState blocking = reader.getBlockState(front);
            return !(appearance(reader, front).getBlock() == material.getBlock()
                    && Block.isFaceFull(blocking.getShape(reader, front), face.getOpposite()));
        } catch (RuntimeException outsideTheRegion) {
            return false;
        }
    }

    private static BlockState appearance(BlockGetter reader, BlockPos pos) {
        BlockState state = reader.getBlockState(pos);
        return state.getBlock() instanceof CopycatBlock ? CopycatBlock.getMaterial(reader, pos) : state;
    }

    private static final class Neighbourhood implements BlockAndTintGetter {
        private final BlockState material;
        private final Direction face;
        private final int mask;

        private Neighbourhood(BlockState material, Direction face, int mask) {
            this.material = material;
            this.face = face;
            this.mask = mask;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            int dx = pos.getX() - ORIGIN.getX(), dy = pos.getY() - ORIGIN.getY(), dz = pos.getZ() - ORIGIN.getZ();
            if (dx == 0 && dy == 0 && dz == 0) return this.material;
            if (this.face.getAxis().choose(dx, dy, dz) != 0) return Blocks.AIR.defaultBlockState();
            int dA = axisA(this.face).choose(dx, dy, dz), dB = axisB(this.face).choose(dx, dy, dz);
            if (Math.abs(dA) > 1 || Math.abs(dB) > 1) return Blocks.AIR.defaultBlockState();
            return (this.mask & bit(dA, dB)) != 0 ? this.material : Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return 1.0F;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return Minecraft.getInstance().level.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            return 0xFFFFFF;
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }

    private CopycatFacadeConnections() {}
}
