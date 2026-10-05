package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class CompressionGunStateModel {
    private static final java.util.Set<ResourceLocation> SHEETS = java.util.Set.of(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "item/compression_gun"),
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "item/compression_gun_pearlescent"));

    private static final Cell NOSE = new Cell(36, 0, 8, 8, 9);
    private static final Cell DISPLAY = new Cell(45, 0, 4, 4, 5);

    private static final int NOSE_DARK = 0;
    private static final int NOSE_SHRINK = 1;
    private static final int NOSE_GROW = 2;

    private static final int DISPLAY_SHRINK = 0;
    private static final int DISPLAY_GROW = 1;
    private static final int DISPLAY_EMPTY = 2;

    enum State { IDLE, ACTIVE, EMPTY }

    private record Cell(int x, int y, int width, int height, int stride) {}

    private static final class Entry {
        private final BakedModel[] variants = new BakedModel[9];
        private Vec3 muzzle;
    }

    private static final int MAX_ENTRIES = 4;
    private static final Map<BakedModel, Entry> ENTRIES = new java.util.LinkedHashMap<>(8, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<BakedModel, Entry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private CompressionGunStateModel() {}

    static BakedModel get(final BakedModel body, final State state, final boolean growing) {
        final Entry entry = entry(body);
        final int nose = state == State.ACTIVE ? (growing ? NOSE_GROW : NOSE_SHRINK) : NOSE_DARK;
        final int display = state == State.EMPTY ? DISPLAY_EMPTY : growing ? DISPLAY_GROW : DISPLAY_SHRINK;
        final int key = nose * 3 + display;
        if (entry.variants[key] == null) entry.variants[key] = new Variant(body, nose, display);
        return entry.variants[key];
    }

    static Vec3 muzzle(final BakedModel body) {
        final Entry entry = entry(body);
        if (entry.muzzle == null) entry.muzzle = findMuzzle(body);
        return entry.muzzle;
    }

    private static Entry entry(final BakedModel body) {
        return ENTRIES.computeIfAbsent(body, ignored -> new Entry());
    }

    private static Vec3 findMuzzle(final BakedModel body) {
        final RandomSource random = RandomSource.create(42L);
        double x = 0, y = 0, z = 0;
        int count = 0;
        for (final BakedQuad quad : allQuads(body, random)) {
            if (cellOf(quad) != NOSE) continue;
            final int[] data = quad.getVertices();
            for (int i = 0; i < 4; i++) {
                final int o = i * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
                x += Float.intBitsToFloat(data[o]);
                y += Float.intBitsToFloat(data[o + 1]);
                z += Float.intBitsToFloat(data[o + 2]);
                count++;
            }
        }
        return count == 0 ? new Vec3(0.5D, 0.5D, 0.0D) : new Vec3(x / count, y / count, z / count);
    }

    private static List<BakedQuad> allQuads(final BakedModel model, final RandomSource random) {
        final List<BakedQuad> quads = new ArrayList<>(model.getQuads(null, null, random));
        for (final Direction direction : Direction.values()) quads.addAll(model.getQuads(null, direction, random));
        return quads;
    }

    @Nullable
    private static Cell cellOf(final BakedQuad quad) {
        final TextureAtlasSprite sprite = quad.getSprite();
        if (!SHEETS.contains(sprite.contents().name())) return null;
        final int[] data = quad.getVertices();
        float u = 0, v = 0;
        for (int i = 0; i < 4; i++) {
            final int o = i * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
            u += Float.intBitsToFloat(data[o]);
            v += Float.intBitsToFloat(data[o + 1]);
        }
        final float px = (u / 4 - sprite.getU0()) / (sprite.getU1() - sprite.getU0()) * sprite.contents().width();
        final float py = (v / 4 - sprite.getV0()) / (sprite.getV1() - sprite.getV0()) * sprite.contents().height();
        for (final Cell cell : new Cell[] {NOSE, DISPLAY}) {
            if (px >= cell.x() && px <= cell.x() + cell.width() && py >= cell.y() && py <= cell.y() + cell.height()) {
                return cell;
            }
        }
        return null;
    }

    private static final class Variant extends BakedModelWrapper<BakedModel> {
        private final int nose;
        private final int display;
        private final Map<List<BakedQuad>, List<BakedQuad>> cache = new IdentityHashMap<>();

        private Variant(final BakedModel body, final int nose, final int display) {
            super(body);
            this.nose = nose;
            this.display = display;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable final BlockState state, @Nullable final Direction side,
                                        final RandomSource rand) {
            return cache.computeIfAbsent(super.getQuads(state, side, rand), this::remap);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable final BlockState state, @Nullable final Direction side,
                                        final RandomSource rand, final ModelData data,
                                        @Nullable final RenderType renderType) {
            return cache.computeIfAbsent(super.getQuads(state, side, rand, data, renderType), this::remap);
        }

        @Override
        public List<BakedModel> getRenderPasses(final ItemStack stack, final boolean fabulous) {
            return List.of(this);
        }

        private List<BakedQuad> remap(final List<BakedQuad> quads) {
            final List<BakedQuad> out = new ArrayList<>(quads.size());
            for (final BakedQuad quad : quads) out.add(remap(quad));
            return List.copyOf(out);
        }

        private BakedQuad remap(final BakedQuad quad) {
            final Cell cell = cellOf(quad);
            if (cell == null) return quad;
            final int row = cell == NOSE ? nose : display;
            final boolean lit = cell == NOSE && row != NOSE_DARK;
            if (row == 0 && !lit) return quad;

            final TextureAtlasSprite sprite = quad.getSprite();
            final float shift = row * cell.stride() * (sprite.getV1() - sprite.getV0()) / sprite.contents().height();
            final int[] data = quad.getVertices().clone();
            for (int i = 0; i < 4; i++) {
                final int o = i * IQuadTransformer.STRIDE + IQuadTransformer.UV0 + 1;
                data[o] = Float.floatToRawIntBits(Float.intBitsToFloat(data[o]) + shift);
            }
            final BakedQuad moved = new BakedQuad(data, quad.getTintIndex(), quad.getDirection(), sprite,
                    quad.isShade(), quad.hasAmbientOcclusion());
            if (lit) QuadTransformers.settingMaxEmissivity().processInPlace(moved);
            return moved;
        }
    }
}
