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
        public int mask(final Direction face, final int tileA, final int tileB) {
            final Face entry = this.faces[face.get3DDataValue()];
            return entry == null ? -1 : entry.mask(tileA, tileB);
        }
    }

    private record Face(int tileA, int tileB, int countA, int countB, short[] masks) {
        private int mask(final int a, final int b) {
            final int i = a - this.tileA, j = b - this.tileB;
            if (i < 0 || j < 0 || i >= this.countA || j >= this.countB) return -1;
            return this.masks[i * this.countB + j];
        }
    }

    private record CellKey(BlockState material, Direction face, Direction side, RenderType renderType, int mask) {}

    public static boolean connects(final BlockState material) {
        return CopycatModel.getModelOf(material) instanceof CTModel;
    }

    public static Faces gather(
            final BlockAndTintGetter world,
            final BlockPos pos,
            final CopycatFacadeFrames.Frame frame,
            final BlockState material
    ) {
        final Face[] faces = new Face[6];
        for (final Direction face : Direction.values()) {
            final Direction.Axis axisA = axisA(face), axisB = axisB(face);
            final int blockA = frame.along(axisA), blockB = frame.along(axisB);
            final int[] rangeA = FacadeTiling.tileRange(blockA, frame.scale());
            final int[] rangeB = FacadeTiling.tileRange(blockB, frame.scale());
            final int countA = rangeA[1] - rangeA[0] + 1, countB = rangeB[1] - rangeB[0] + 1;
            if (countA <= 0 || countB <= 0 || countA * countB > MAX_TILES) continue;

            final Map<Long, Boolean> probed = new HashMap<>();
            final short[] masks = new short[countA * countB];
            for (int i = 0; i < countA; i++) {
                final int[] acrossA = FacadeTiling.blocksAcross(rangeA[0] + i, frame.scale());
                for (int j = 0; j < countB; j++) {
                    final int[] acrossB = FacadeTiling.blocksAcross(rangeB[0] + j, frame.scale());
                    int mask = 0;
                    for (int dA = -1; dA <= 1; dA++) {
                        for (int dB = -1; dB <= 1; dB++) {
                            if (dA == 0 && dB == 0) continue;
                            final int offsetA = dA == 0 ? 0 : (dA < 0 ? acrossA[0] : acrossA[1]) - blockA;
                            final int offsetB = dB == 0 ? 0 : (dB < 0 ? acrossB[0] : acrossB[1]) - blockB;
                            final boolean connected = offsetA == 0 && offsetB == 0
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
            final BlockState material,
            final Direction face,
            final Direction side,
            final RenderType renderType,
            final int mask
    ) {
        return CELLS.computeIfAbsent(new CellKey(material, face, side, renderType, mask), key -> {
            final var model = CopycatModel.getModelOf(material);
            final ModelData data = model.getModelData(new Neighbourhood(material, face, mask), ORIGIN, material, ModelData.EMPTY);
            return List.copyOf(model.getQuads(material, side, RandomSource.create(PINNED_SEED), data, renderType));
        });
    }

    public static void clear() {
        CELLS.clear();
    }

    public static Direction.Axis axisA(final Direction face) {
        return face.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    public static Direction.Axis axisB(final Direction face) {
        return face.getAxis() == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;
    }

    private static int bit(final int dA, final int dB) {
        return 1 << ((dA + 1) * 3 + (dB + 1));
    }

    private static boolean probe(
            final BlockAndTintGetter world,
            final BlockPos pos,
            final Direction face,
            final Direction.Axis axisA,
            final Direction.Axis axisB,
            final int offsetA,
            final int offsetB,
            final BlockState material
    ) {
        final BlockGetter reader = Math.max(Math.abs(offsetA), Math.abs(offsetB)) <= REGION_REACH
                || CopycatFacadeFrames.previewing()
                || CopycatFacadeFrames.contraptionWorld(world) != null
                || Minecraft.getInstance().level == null
                ? world
                : Minecraft.getInstance().level;
        final BlockPos neighbour = pos.relative(axisA, offsetA).relative(axisB, offsetB);
        try {
            if (appearance(reader, neighbour).getBlock() != material.getBlock()) return false;
            final BlockPos front = neighbour.relative(face);
            final BlockState blocking = reader.getBlockState(front);
            return !(appearance(reader, front).getBlock() == material.getBlock()
                    && Block.isFaceFull(blocking.getShape(reader, front), face.getOpposite()));
        } catch (final RuntimeException outsideTheRegion) {
            return false;
        }
    }

    private static BlockState appearance(final BlockGetter reader, final BlockPos pos) {
        final BlockState state = reader.getBlockState(pos);
        return state.getBlock() instanceof CopycatBlock ? CopycatBlock.getMaterial(reader, pos) : state;
    }

    private static final class Neighbourhood implements BlockAndTintGetter {
        private final BlockState material;
        private final Direction face;
        private final int mask;

        private Neighbourhood(final BlockState material, final Direction face, final int mask) {
            this.material = material;
            this.face = face;
            this.mask = mask;
        }

        @Override
        public BlockState getBlockState(final BlockPos pos) {
            final int dx = pos.getX() - ORIGIN.getX(), dy = pos.getY() - ORIGIN.getY(), dz = pos.getZ() - ORIGIN.getZ();
            if (dx == 0 && dy == 0 && dz == 0) return this.material;
            if (this.face.getAxis().choose(dx, dy, dz) != 0) return Blocks.AIR.defaultBlockState();
            final int dA = axisA(this.face).choose(dx, dy, dz), dB = axisB(this.face).choose(dx, dy, dz);
            if (Math.abs(dA) > 1 || Math.abs(dB) > 1) return Blocks.AIR.defaultBlockState();
            return (this.mask & bit(dA, dB)) != 0 ? this.material : Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(final BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(final BlockPos pos) {
            return null;
        }

        @Override
        public float getShade(final Direction direction, final boolean shade) {
            return 1.0F;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return Minecraft.getInstance().level.getLightEngine();
        }

        @Override
        public int getBlockTint(final BlockPos pos, final ColorResolver resolver) {
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
