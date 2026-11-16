package com.misterblusky9.pocket.block;

import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;

public enum DummyHead implements SkullBlock.Type {
    TYPE;

    DummyHead() {
        TYPES.put(getSerializedName(), this);
    }

    @Override
    public String getSerializedName() {
        return "pocket_dummy_head";
    }

    public static void addToSkullEntity(BlockEntityTypeAddBlocksEvent event) {
        event.modify(BlockEntityType.SKULL, ModBlocks.DUMMY_HEAD.get(), ModBlocks.DUMMY_WALL_HEAD.get());
    }
}
