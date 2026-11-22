package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.decoration.copycat.CopycatModel;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FacadeSkin {
    private static final long LINGER_MILLIS = 1000L;
    private static final Map<ClientSubLevel, Long> SKINNED = new ConcurrentHashMap<>();
    private static final Map<Block, Boolean> FACADABLE = new ConcurrentHashMap<>();

    private FacadeSkin() {}

    public static void show(ClientSubLevel mirror) {
        if (mirror.isRemoved()) return;
        if (SKINNED.put(mirror, Util.getMillis()) == null) dirty(mirror);
    }

    public static void tick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) {
            SKINNED.clear();
            FACADABLE.clear();
            return;
        }
        long now = Util.getMillis();
        SKINNED.entrySet().removeIf(entry -> {
            ClientSubLevel mirror = entry.getKey();
            if (mirror.isRemoved()) return true;
            if (now - entry.getValue() <= LINGER_MILLIS) return false;
            dirty(mirror);
            return true;
        });
    }

    public static boolean render(BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos, BlockAndTintGetter level,
                                 PoseStack pose, VertexConsumer consumer, boolean checkSides, RandomSource random,
                                 RenderType renderType) {
        if (SKINNED.isEmpty() || !inside(pos) || !facadable(state)) return false;
        BakedModel facade = dispatcher.getBlockModel(ModBlocks.COPYCAT_FACADE.get().defaultBlockState());
        ModelData data = facade.getModelData(level, pos, state,
                ModelData.builder().with(CopycatModel.MATERIAL_PROPERTY, state).build());
        dispatcher.getModelRenderer().tesselateBlock(level, facade, state, pos, pose, consumer, checkSides, random,
                state.getSeed(pos), OverlayTexture.NO_OVERLAY, data, renderType);
        return true;
    }

    private static boolean inside(BlockPos pos) {
        for (ClientSubLevel mirror : SKINNED.keySet()) {
            BoundingBox3ic bounds = mirror.getPlot().getBoundingBox();
            if (pos.getX() >= bounds.minX() && pos.getX() <= bounds.maxX()
                    && pos.getY() >= bounds.minY() && pos.getY() <= bounds.maxY()
                    && pos.getZ() >= bounds.minZ() && pos.getZ() <= bounds.maxZ()) return true;
        }
        return false;
    }

    private static boolean facadable(BlockState state) {
        Block block = state.getBlock();
        Boolean known = FACADABLE.get(block);
        if (known != null) return known;
        Level level = Minecraft.getInstance().level;
        if (level == null) return false;
        BlockState accepted = ModBlocks.COPYCAT_FACADE.get().getAcceptedBlockState(level, BlockPos.ZERO, new ItemStack(block), null);
        boolean result = accepted != null && accepted.is(block);
        FACADABLE.put(block, result);
        return result;
    }

    private static void dirty(ClientSubLevel mirror) {
        var renderData = mirror.getRenderData();
        if (renderData == null) return;
        BoundingBox3ic bounds = mirror.getPlot().getBoundingBox();
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++)
            for (int y = bounds.minY() >> 4; y <= bounds.maxY() >> 4; y++)
                for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++)
                    renderData.setDirty(x, y, z, false);
    }
}
