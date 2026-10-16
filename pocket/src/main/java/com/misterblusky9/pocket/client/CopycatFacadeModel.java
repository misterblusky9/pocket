package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.FacadeTiling;
import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pym.api.ScaleBounds;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import com.simibubi.create.content.decoration.copycat.CopycatModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.ArrayList;
import java.util.List;

public final class CopycatFacadeModel extends CopycatModel {
    private static final ChunkRenderTypeSet LAYERS = ChunkRenderTypeSet.of(
            RenderType.solid(), RenderType.cutout(), RenderType.cutoutMipped(), RenderType.translucent());
    private static final ModelProperty<CopycatFacadeFrames.Frame> FRAME = new ModelProperty<>();
    private static final ModelProperty<CopycatFacadeConnections.Faces> CONNECTIONS = new ModelProperty<>();
    private static final long PINNED_SEED = 42L;
    private static final float PLANE_TOLERANCE = 1.0E-4F;

    private CopycatFacadeModel(BakedModel originalModel) {
        super(originalModel);
    }

    public static void swap(ModelEvent.ModifyBakingResult event) {
        CopycatFacadeConnections.clear();
        for (BlockState state : ModBlocks.COPYCAT_FACADE.get().getStateDefinition().getPossibleStates()) {
            ModelResourceLocation location = BlockModelShaper.stateToModelLocation(state);
            BakedModel original = event.getModels().get(location);
            if (original != null) event.getModels().put(location, new CopycatFacadeModel(original));
        }
    }

    public static void registerColours(RegisterColorHandlersEvent.Block event) {
        event.register(CopycatBlock.wrappedColor(), ModBlocks.COPYCAT_FACADE.get());
    }

    @Override
    protected ModelData.Builder gatherModelData(
            ModelData.Builder builder,
            BlockAndTintGetter world,
            BlockPos pos,
            BlockState state,
            ModelData blockEntityData
    ) {
        super.gatherModelData(builder, world, pos, state, blockEntityData);
        CopycatFacadeFrames.Frame frame = CopycatFacadeFrames.frameAt(world, pos);
        if (frame == null) return builder;
        builder.with(FRAME, frame);

        BlockState material = getMaterial(blockEntityData);
        if (!AllBlocks.COPYCAT_BASE.has(material)
                && !ScaleBounds.same(frame.scale(), ScaleBounds.FULL)
                && CopycatFacadeConnections.connects(material)) {
            builder.with(CONNECTIONS, CopycatFacadeConnections.gather(world, pos, frame, material));
        }
        return builder;
    }

    @Override
    public List<BakedQuad> getQuads(
            BlockState state,
            Direction side,
            RandomSource rand,
            ModelData data,
            RenderType renderType
    ) {
        CopycatFacadeFrames.Frame frame = data.get(FRAME);
        BlockState material = getMaterial(data);
        if (AllBlocks.COPYCAT_BASE.has(material) || frame == null
                || ScaleBounds.same(frame.scale(), ScaleBounds.FULL)) {
            return super.getQuads(state, side, rand, data, renderType);
        }
        CopycatFacadeConnections.Faces connections = data.get(CONNECTIONS);
        return retile(super.getQuads(state, side, RandomSource.create(PINNED_SEED), data, renderType),
                frame, connections, material, side, renderType);
    }

    @Override
    protected List<BakedQuad> getCroppedQuads(
            BlockState state,
            Direction side,
            RandomSource rand,
            BlockState material,
            ModelData wrappedData,
            RenderType renderType
    ) {
        return modelOf(material).getQuads(material, side, rand, wrappedData, renderType);
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        BlockState material = getMaterial(data);
        if (!AllBlocks.COPYCAT_BASE.has(material)) return super.getParticleIcon(data);
        return CopycatFacadePartials.BASE.get().getParticleIcon(ModelData.EMPTY);
    }

    private static BakedModel modelOf(BlockState material) {
        return AllBlocks.COPYCAT_BASE.has(material) ? CopycatFacadePartials.BASE.get() : getModelOf(material);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return LAYERS;
    }

    private static List<BakedQuad> retile(
            List<BakedQuad> quads,
            CopycatFacadeFrames.Frame frame,
            CopycatFacadeConnections.Faces connections,
            BlockState material,
            Direction side,
            RenderType renderType
    ) {
        if (quads.isEmpty()) return quads;
        List<BakedQuad> tiled = new ArrayList<>(quads.size());
        for (int index = 0; index < quads.size(); index++) {
            retile(quads, index, frame, connections, material, side, renderType, tiled);
        }
        return tiled;
    }

    private static void retile(
            List<BakedQuad> quads,
            int index,
            CopycatFacadeFrames.Frame frame,
            CopycatFacadeConnections.Faces connections,
            BlockState material,
            Direction side,
            RenderType renderType,
            List<BakedQuad> out
    ) {
        BakedQuad quad = quads.get(index);
        Direction direction = quad.getDirection();
        Direction.Axis normal = direction.getAxis();
        Direction.Axis axisA = CopycatFacadeConnections.axisA(direction);
        Direction.Axis axisB = CopycatFacadeConnections.axisB(direction);
        int[] vertices = quad.getVertices();
        Face face = face(quad, normal, axisA, axisB);
        if (face == null) {
            out.add(quad);
            return;
        }

        double[] u = new double[4], v = new double[4];
        for (int i = 0; i < 4; i++) {
            u[i] = (face.u()[i] - face.rect()[0]) / (face.rect()[1] - face.rect()[0]);
            v[i] = (face.v()[i] - face.rect()[2]) / (face.rect()[3] - face.rect()[2]);
        }

        int blockA = frame.along(axisA), blockB = frame.along(axisB);
        List<FacadeTiling.Piece> pieces = FacadeTiling.retile(
                face.a(), face.b(), u, v, blockA, blockB, frame.scale());
        if (pieces == null) {
            out.add(quad);
            return;
        }

        for (FacadeTiling.Piece piece : pieces) {
            double[] rect = cellRect(quads, index, piece, face.rect(), frame, connections, material, side, renderType,
                    normal, axisA, axisB, blockA, blockB, direction);
            int[] cut = vertices.clone();
            for (int i = 0; i < 4; i++) {
                int base = i * IQuadTransformer.STRIDE;
                setPosition(cut, i, normal, face.plane());
                setPosition(cut, i, axisA, (float) piece.a()[i]);
                setPosition(cut, i, axisB, (float) piece.b()[i]);
                cut[base + IQuadTransformer.UV0] = Float.floatToRawIntBits((float) (rect[0] + piece.u()[i] * (rect[1] - rect[0])));
                cut[base + IQuadTransformer.UV0 + 1] = Float.floatToRawIntBits((float) (rect[2] + piece.v()[i] * (rect[3] - rect[2])));
            }
            out.add(new BakedQuad(cut, quad.getTintIndex(), direction, quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion()));
        }
    }

    private static double[] cellRect(
            List<BakedQuad> quads,
            int index,
            FacadeTiling.Piece piece,
            double[] own,
            CopycatFacadeFrames.Frame frame,
            CopycatFacadeConnections.Faces connections,
            BlockState material,
            Direction side,
            RenderType renderType,
            Direction.Axis normal,
            Direction.Axis axisA,
            Direction.Axis axisB,
            int blockA,
            int blockB,
            Direction direction
    ) {
        if (connections == null) return own;
        double midA = (piece.a()[0] + piece.a()[2]) * 0.5D, midB = (piece.b()[0] + piece.b()[2]) * 0.5D;
        int mask = connections.mask(direction,
                FacadeTiling.tileOf(blockA, midA, frame.scale()), FacadeTiling.tileOf(blockB, midB, frame.scale()));
        if (mask < 0) return own;

        List<BakedQuad> cell = CopycatFacadeConnections.cell(material, direction, side, renderType, mask);
        if (cell.size() != quads.size()) return own;
        Face shifted = face(cell.get(index), normal, axisA, axisB);
        return shifted == null ? own : shifted.rect();
    }

    private record Face(double[] a, double[] b, double[] u, double[] v, double[] rect, float plane) {}

    private static Face face(
            BakedQuad quad,
            Direction.Axis normal,
            Direction.Axis axisA,
            Direction.Axis axisB
    ) {
        int[] vertices = quad.getVertices();
        if (vertices.length < 4 * IQuadTransformer.STRIDE) return null;
        double[] a = new double[4], b = new double[4], u = new double[4], v = new double[4];
        float plane = position(vertices, 0, normal);
        for (int i = 0; i < 4; i++) {
            if (Math.abs(position(vertices, i, normal) - plane) > PLANE_TOLERANCE) return null;
            a[i] = position(vertices, i, axisA);
            b[i] = position(vertices, i, axisB);
            u[i] = Float.intBitsToFloat(vertices[i * IQuadTransformer.STRIDE + IQuadTransformer.UV0]);
            v[i] = Float.intBitsToFloat(vertices[i * IQuadTransformer.STRIDE + IQuadTransformer.UV0 + 1]);
        }
        double[] rect = FacadeTiling.impliedRect(a, b, u, v);
        return rect == null ? null : new Face(a, b, u, v, rect, plane);
    }

    private static float position(int[] vertices, int vertex, Direction.Axis axis) {
        return Float.intBitsToFloat(vertices[vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION + axis.ordinal()]);
    }

    private static void setPosition(int[] vertices, int vertex, Direction.Axis axis, float value) {
        vertices[vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION + axis.ordinal()] = Float.floatToRawIntBits(value);
    }
}
