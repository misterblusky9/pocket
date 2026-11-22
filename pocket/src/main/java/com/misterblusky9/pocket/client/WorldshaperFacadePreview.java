package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.ModBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class WorldshaperFacadePreview {
    public static BlockState shown(BlockState state, ItemStack shaper) {
        if (state == null || !state.is(ModBlocks.COPYCAT_FACADE.get())) return state;
        CompoundTag data = shaper.get(AllDataComponents.SHAPER_BLOCK_DATA);
        var level = Minecraft.getInstance().level;
        if (data == null || level == null || !data.contains("Material", Tag.TAG_COMPOUND)) return state;
        BlockState material = NbtUtils.readBlockState(
                level.holderLookup(Registries.BLOCK), data.getCompound("Material"));
        return material.isAir() || AllBlocks.COPYCAT_BASE.has(material) ? state : material;
    }

    private WorldshaperFacadePreview() {}
}
