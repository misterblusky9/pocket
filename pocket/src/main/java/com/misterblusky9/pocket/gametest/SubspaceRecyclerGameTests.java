package com.misterblusky9.pocket.gametest;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.pocket.PocketedSubLevelRecycling;
import com.misterblusky9.pocket.pocket.PocketedSubLevelSavedData;
import com.mojang.serialization.Codec;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(PocketSized.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SubspaceRecyclerGameTests {
    private static final Codec<PalettedContainer<BlockState>> STATES = PalettedContainer.codecRW(
            Block.BLOCK_STATE_REGISTRY, BlockState.CODEC,
            PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());

    private record Cell(int x, int y, int z, BlockState state) {}

    @GameTest(template = "recycler_empty", timeoutTicks = 160)
    public static void basicBlocksAndFluids(GameTestHelper test) {
        List<Cell> blocks = List.of(
                new Cell(0, 0, 0, Blocks.STONE.defaultBlockState()),
                new Cell(1, 0, 0, Blocks.GLASS.defaultBlockState()),
                new Cell(2, 0, 0, Blocks.IRON_BARS.defaultBlockState()),
                new Cell(3, 0, 0, Blocks.WATER.defaultBlockState()),
                new Cell(4, 0, 0, Blocks.LAVA.defaultBlockState()));
        var result = scan(test, blocks, List.of(), List.of());
        assertDrops(test, result.drops(), Map.of(
                Items.STONE, 1, Items.GLASS, 1, Items.IRON_BARS, 1,
                ModItems.EMPTY_BOX.get(), 1));
        test.succeed();
    }

    @GameTest(template = "recycler_empty", timeoutTicks = 160)
    public static void furnaceOrphanAndFrames(GameTestHelper test) {
        ServerLevel level = test.getLevel();
        List<Cell> blocks = List.of(new Cell(0, 0, 0, Blocks.FURNACE.defaultBlockState()));
        CompoundTag furnace = blockEntity(level, "minecraft:furnace", 0, 0, 0);
        furnace.put("Items", items(level, Items.DIAMOND, 3));
        CompoundTag orphan = blockEntity(level, "minecraft:furnace", 2, 0, 0);
        orphan.put("Items", items(level, Items.EMERALD, 2));
        CompoundTag frame = new CompoundTag();
        frame.putString("id", "minecraft:item_frame");
        frame.put("Item", new ItemStack(Items.GOLD_INGOT, 1).save(level.registryAccess()));
        var result = scan(test, blocks, List.of(furnace, orphan), List.of(frame));
        assertDrops(test, result.drops(), Map.of(
                Items.FURNACE, 2, Items.DIAMOND, 3, Items.EMERALD, 2,
                Items.ITEM_FRAME, 1, Items.GOLD_INGOT, 1,
                ModItems.EMPTY_BOX.get(), 1));
        test.succeed();
    }

    @GameTest(template = "recycler_empty", timeoutTicks = 400)
    public static void registeredBlockCatalog(GameTestHelper test) {
        List<Cell> cells = new ArrayList<>();
        Map<Item, Integer> expected = new IdentityHashMap<>();
        int index = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            Item item = block.asItem();
            if (item == Items.AIR || state.isAir()) continue;
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER)
                continue;
            if (state.hasProperty(BedBlock.PART) && state.getValue(BedBlock.PART) == BedPart.HEAD)
                continue;
            cells.add(new Cell(index & 15, index >>> 8, (index >>> 4) & 15, state));
            expected.merge(item, 1, Integer::sum);
            index++;
        }
        test.assertTrue(index > 100, "Unexpectedly small loaded block registry");
        expected.merge(ModItems.EMPTY_BOX.get(), 1, Integer::sum);
        var result = scan(test, cells, List.of(), List.of());
        assertDrops(test, result.drops(), expected);
        test.succeed();
    }

    private static void assertDrops(GameTestHelper test, List<ItemStack> actualStacks,
                                    Map<Item, Integer> expected) {
        Map<Item, Integer> actual = new IdentityHashMap<>();
        for (ItemStack stack : actualStacks)
            if (!stack.isEmpty()) actual.merge(stack.getItem(), stack.getCount(), Integer::sum);
        for (Map.Entry<Item, Integer> e : expected.entrySet()) {
            int got = actual.getOrDefault(e.getKey(), 0);
            test.assertTrue(got == e.getValue(), "Wrong salvage count for "
                    + BuiltInRegistries.ITEM.getKey(e.getKey()) + ": expected "
                    + e.getValue() + ", got " + got);
        }
        for (Map.Entry<Item, Integer> e : actual.entrySet())
            test.assertTrue(expected.containsKey(e.getKey()), "Unexpected salvage item: "
                    + BuiltInRegistries.ITEM.getKey(e.getKey()) + " x" + e.getValue());
    }

    private static CompoundTag blockEntity(ServerLevel source, String id, int x, int y, int z) {
        BlockPos base = plotBase(source);
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putInt("x", base.getX() + x);
        tag.putInt("y", y);
        tag.putInt("z", base.getZ() + z);
        return tag;
    }

    private static ListTag items(ServerLevel level, Item item, int amount) {
        ListTag items = new ListTag();
        CompoundTag stack = (CompoundTag) new ItemStack(item, amount).save(level.registryAccess());
        stack.putByte("Slot", (byte) 0);
        items.add(stack);
        return items;
    }

    private static BlockPos plotBase(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) throw new IllegalStateException("Sable server plot unavailable");
        int side = 1 << (container.getLogPlotSize() + 4);
        return new BlockPos(container.getOrigin().x * side, 0, container.getOrigin().y * side);
    }

    private static PocketedSubLevelRecycling.Plan scan(GameTestHelper test,
            List<Cell> cells, List<CompoundTag> blockEntities, List<CompoundTag> entities) {
        ServerLevel level = test.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        test.assertTrue(container != null, "Sable is not initialized for GameTest dimension");
        Map<Integer, PalettedContainer<BlockState>> sections = new LinkedHashMap<>();
        for (Cell cell : cells) {
            int sectionY = Math.floorDiv(cell.y(), 16);
            PalettedContainer<BlockState> palette = sections.computeIfAbsent(sectionY,
                    ignored -> new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY,
                            Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES));
            palette.getAndSetUnchecked(cell.x(), Math.floorMod(cell.y(), 16), cell.z(), cell.state());
        }
        CompoundTag sectionsTag = new CompoundTag();
        for (var e : sections.entrySet()) {
            CompoundTag section = new CompoundTag();
            section.put("block_states", STATES.encodeStart(NbtOps.INSTANCE, e.getValue()).getOrThrow());
            sectionsTag.put(Integer.toString(e.getKey()), section);
        }
        CompoundTag chunk = new CompoundTag();
        chunk.put("sections", sectionsTag);
        ListTag bes = new ListTag();
        for (CompoundTag be : blockEntities) bes.add(be.copy());
        chunk.put("block_entities", bes);
        CompoundTag chunks = new CompoundTag();
        chunks.put(Long.toString(new ChunkPos(0, 0).toLong()), chunk);
        CompoundTag plot = new CompoundTag();
        plot.putInt("plot_x", 0);
        plot.putInt("plot_z", 0);
        plot.putInt("log_size", container.getLogPlotSize());
        plot.put("chunks", chunks);
        CompoundTag payload = new CompoundTag();
        payload.put("plot", plot);
        ListTag entityTags = new ListTag();
        for (CompoundTag entity : entities) entityTags.add(entity.copy());
        payload.put("pocket_entities", entityTags);

        UUID token = UUID.randomUUID();
        PocketedSubLevelSavedData storage = PocketedSubLevelSavedData.getOrLoad(level);
        ItemStack carrier = PocketCaseItem.createFilled(
                new ItemStack(ModItems.POCKETED_SUBLEVEL.get()), token, level,
                "Recycler GameTest", null, cells.size(), blockEntities.size(), 1.0);
        PocketCaseItem.setContainer(carrier, new ItemStack(ModItems.EMPTY_BOX.get()));
        storage.put(token, payload);
        try {
            return PocketedSubLevelRecycling.plan(level, carrier);
        } finally {
            storage.remove(token);
        }
    }

    private SubspaceRecyclerGameTests() {}
}
