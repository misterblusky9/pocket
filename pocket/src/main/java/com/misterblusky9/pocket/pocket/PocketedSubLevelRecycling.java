package com.misterblusky9.pocket.pocket;

import com.misterblusky9.pocket.item.PocketCaseItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PocketedSubLevelRecycling {
    public record Plan(ServerLevel source, PocketedSubLevelSavedData storage,
                       UUID token, List<ItemStack> drops) {}

    public static Plan plan(ServerLevel current, ItemStack carrier) {
        UUID token = PocketCaseItem.token(carrier);
        if (token == null || !PocketCaseItem.isUnique(carrier))
            throw new IllegalStateException("Not a filled pocketed sublevel");
        ResourceLocation dimension = ResourceLocation.tryParse(PocketCaseItem.sourceDimension(carrier));
        if (dimension == null) throw new IllegalStateException("Invalid pocket source dimension");
        ServerLevel source = current.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (source == null) throw new IllegalStateException("Pocket source dimension is not loaded");
        PocketedSubLevelSavedData storage = PocketedSubLevelSavedData.getOrLoad(source);
        CompoundTag payload = storage.getCopy(token);
        if (payload == null) throw new IllegalStateException("Pocket payload is missing or already consumed");

        CompoundTag plot = payload.getCompound("plot");
        if (!plot.contains("chunks", Tag.TAG_COMPOUND)
                || !plot.contains("plot_x", Tag.TAG_INT)
                || !plot.contains("plot_z", Tag.TAG_INT))
            throw new IllegalStateException("Pocket is missing serialized plot data");
        ServerSubLevelContainer container = SubLevelContainer.getContainer(source);
        if (container == null || plot.getInt("log_size") != container.getLogPlotSize())
            throw new IllegalStateException("Pocket plot encoding differs from the source backend");

        int offsetX = (container.getOrigin().x + plot.getInt("plot_x"))
                * (1 << (container.getLogPlotSize() + 4));
        int offsetZ = (container.getOrigin().y + plot.getInt("plot_z"))
                * (1 << (container.getLogPlotSize() + 4));
        CompoundTag chunks = plot.getCompound("chunks");
        List<ItemStack> results = new ArrayList<>();
        int blockCount = 0;
        int beCount = 0;
        int harmlessOrphans = 0;
        for (String chunkKey : chunks.getAllKeys()) {
            long key;
            try { key = Long.parseLong(chunkKey); }
            catch (NumberFormatException e) { throw new IllegalStateException("Invalid plot chunk key", e); }
            ChunkPos local = new ChunkPos(key);
            CompoundTag chunk = chunks.getCompound(chunkKey);
            Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
            ListTag beTags = chunk.getList("block_entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < beTags.size(); ++i) {
                CompoundTag tag = beTags.getCompound(i);
                if (!tag.contains("id", Tag.TAG_STRING)
                        || !tag.contains("x", Tag.TAG_ANY_NUMERIC)
                        || !tag.contains("y", Tag.TAG_ANY_NUMERIC)
                        || !tag.contains("z", Tag.TAG_ANY_NUMERIC))
                    throw new IllegalStateException("Corrupt stored block entity in " + chunkKey);
                BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
                if (blockEntities.put(pos, tag) != null)
                    throw new IllegalStateException("Duplicate block entity at " + pos);
            }
            CompoundTag sections = chunk.getCompound("sections");
            for (String sectionKey : sections.getAllKeys()) {
                int sectionY;
                try { sectionY = Integer.parseInt(sectionKey); }
                catch (NumberFormatException e) { throw new IllegalStateException("Bad section coordinate", e); }
                PalettedContainer<BlockState> states = NetherEvaporation.readStates(sections.getCompound(sectionKey));
                if (states == null) throw new IllegalStateException("Unreadable block palette in " + sectionKey);
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    BlockState state = states.get(x, y, z);
                    if (state.isAir()) continue;
                    blockCount++;
                    BlockPos pos = new BlockPos(offsetX + local.getMinBlockX() + x,
                            (sectionY << 4) + y, offsetZ + local.getMinBlockZ() + z);
                    CompoundTag beTag = blockEntities.remove(pos);
                    BlockEntity be = null;
                    if (beTag != null) {
                        beCount++;
                        try {
                            be = BlockEntity.loadStatic(pos, state, beTag, source.registryAccess());
                        } catch (RuntimeException ignored) {
                        }
                        boolean rolledLoot = false;
                        if (beTag.contains("LootTable", Tag.TAG_STRING)) {
                            try {
                                ResourceLocation lootId = ResourceLocation.tryParse(beTag.getString("LootTable"));
                                if (lootId == null) throw new IllegalArgumentException("invalid loot ID");
                                LootParams lootContext = new LootParams.Builder(source)
                                        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                                        .create(LootContextParamSets.CHEST);
                                results.addAll(source.getServer().reloadableRegistries()
                                        .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, lootId))
                                        .getRandomItems(lootContext, beTag.getLong("LootTableSeed")));
                                rolledLoot = true;
                            } catch (RuntimeException ignored) {
                            }
                        }
                        int storedItems = 0;
                        if (!rolledLoot && be instanceof Container inventory) {
                            try {
                                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                                    ItemStack content = inventory.getItem(slot);
                                    if (!content.isEmpty()) {
                                        results.add(content.copy());
                                        storedItems++;
                                    }
                                }
                            } catch (RuntimeException ignored) {
                            }
                        }
                        if (!rolledLoot && storedItems == 0 && containsInventoryData(beTag))
                            recoverSerializedInventories(beTag, source, results);
                    }
                    if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                            && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER)
                        continue;
                    if (state.hasProperty(BedBlock.PART)
                            && state.getValue(BedBlock.PART) == BedPart.HEAD)
                        continue;
                    Item blockItem = state.getBlock().asItem();
                    if (blockItem != Items.AIR) {
                        ItemStack physical = new ItemStack(blockItem);
                        if (be instanceof DecoratedPotBlockEntity pot) {
                            physical = pot.getPotAsItem();
                        } else if (be != null && !(be instanceof Container)
                                && (beTag == null || !containsInventoryData(beTag))) {
                            try {
                                be.saveToItem(physical, source.registryAccess());
                            } catch (RuntimeException ignored) {
                            }
                        }
                        results.add(physical);
                    } else {
                        try {
                            for (ItemStack drop : Block.getDrops(state, source, pos, be))
                                if (!drop.isEmpty()) results.add(drop.copy());
                        } catch (RuntimeException ignored) {
                        }
                    }
                }
            }
            for (Map.Entry<BlockPos, CompoundTag> orphan : blockEntities.entrySet()) {
                CompoundTag tag = orphan.getValue();
                harmlessOrphans++;
                if (containsInventoryData(tag)) recoverSerializedInventories(tag, source, results);
                ItemStack recovered = orphanBlockItem(tag.getString("id"));
                if (!recovered.isEmpty()) results.add(recovered);
            }
        }
        CompoundTag integrity = payload.getCompound("pocket_integrity");
        if (integrity.contains("blocks", Tag.TAG_ANY_NUMERIC)
                && integrity.getInt("blocks") != blockCount)
            throw new IllegalStateException("Pocket block count failed integrity check: " + blockCount);
        if (integrity.contains("block_entities", Tag.TAG_ANY_NUMERIC)) {
            int expected = integrity.getInt("block_entities");
            if (expected != beCount && expected != beCount + harmlessOrphans)
                throw new IllegalStateException("Pocket block entity count failed integrity check: "
                        + beCount + " active, " + harmlessOrphans + " stale, expected " + expected);
        }
        if (blockCount == 0 && PocketCaseItem.customTag(carrier) != null
                && PocketCaseItem.customTag(carrier).getInt("pocket_blocks") > 0)
            throw new IllegalStateException("Pocket contains no blocks but carrier expects some");

        ListTag entities = payload.getList("pocket_entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag entityTag = entities.getCompound(i).copy();
            entityTag.remove("pocket_world_space");
            Entity entity = null;
            try {
                entity = EntityType.loadEntityRecursive(entityTag, source, e -> e);
            } catch (RuntimeException ignored) {
            }
            if (entity == null) {
                String savedId = entityTag.getString("id");
                if ("minecraft:item_frame".equals(savedId))
                    results.add(new ItemStack(Items.ITEM_FRAME));
                else if ("minecraft:glow_item_frame".equals(savedId))
                    results.add(new ItemStack(Items.GLOW_ITEM_FRAME));

                if (entityTag.contains("Item", Tag.TAG_COMPOUND)) {
                    CompoundTag itemTag = entityTag.getCompound("Item");
                    ItemStack dropped = decodeItemStack(source, itemTag);
                    if (!dropped.isEmpty()) results.add(dropped.copy());
                }
                if (containsInventoryData(entityTag)) recoverSerializedInventories(entityTag, source, results);
                continue;
            }
            if (containsInventoryData(entityTag) && !(entity instanceof Container)
                    && !(entity instanceof ItemEntity))
                recoverSerializedInventories(entityTag, source, results);
            try {
                collectEntity(source, entity, results);
            } catch (RuntimeException ignored) {
            }
        }
        Item emptyCarrier = PocketCaseItem.containerItem(carrier);
        if (emptyCarrier != Items.AIR) results.add(new ItemStack(emptyCarrier));
        return new Plan(source, storage, token, results);
    }

    private static void collectEntity(ServerLevel source, Entity entity, List<ItemStack> results) {
        if (entity instanceof ItemEntity dropped) {
            if (!dropped.getItem().isEmpty()) results.add(dropped.getItem().copy());
        } else if (entity instanceof ItemFrame frame) {
            results.add(new ItemStack(frame instanceof GlowItemFrame
                    ? Items.GLOW_ITEM_FRAME : Items.ITEM_FRAME));
            ItemStack displayed = frame.getItem();
            if (!displayed.isEmpty()) results.add(displayed.copy());
        } else if (entity instanceof LivingEntity living) {
            LootParams context = new LootParams.Builder(source)
                    .withParameter(LootContextParams.THIS_ENTITY, living)
                    .withParameter(LootContextParams.ORIGIN, living.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, source.damageSources().generic())
                    .create(LootContextParamSets.ENTITY);
            try {
                results.addAll(source.getServer().reloadableRegistries()
                        .getLootTable(living.getLootTable()).getRandomItems(context));
            } catch (RuntimeException ignored) {
            }
            for (ItemStack equipped : living.getAllSlots())
                if (!equipped.isEmpty()) results.add(equipped.copy());
        } else if (!(entity instanceof ExperienceOrb)) {
            ItemStack picked = entity.getPickResult();
            if (picked != null && !picked.isEmpty()) results.add(picked.copy());
        }
        if (entity instanceof Container inventory) {
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack item = inventory.getItem(slot);
                if (!item.isEmpty()) results.add(item.copy());
            }
        }
        for (Entity passenger : entity.getPassengers()) collectEntity(source, passenger, results);
    }

    private static boolean recoverSerializedInventories(CompoundTag root, ServerLevel level,
                                                         List<ItemStack> output) {
        return recoverInventoryNode(root, level, output, false);
    }

    private static boolean recoverInventoryNode(Tag tag, ServerLevel level,
                                                List<ItemStack> output, boolean inventoryContext) {
        if (tag instanceof ListTag list) {
            if (inventoryContext) {
                boolean complete = true;
                for (int i = 0; i < list.size(); i++) {
                    Tag child = list.get(i);
                    if (!(child instanceof CompoundTag entry)) { complete = false; continue; }
                    if (isSerializedItemStack(entry)) {
                        ItemStack stack = decodeItemStack(level, entry);
                        if (stack.isEmpty()) {
                            if (isExplicitlyEmpty(entry)) continue;
                            complete = false;
                            continue;
                        }
                        output.add(stack.copy());
                    } else if (entry.contains("Stack", Tag.TAG_COMPOUND)) {
                        if (!recoverInventoryNode(entry.getCompound("Stack"), level, output, true))
                            complete = false;
                    } else if (!entry.isEmpty() && !isEmptySlotEntry(entry)) {
                        complete = false;
                    }
                }
                return complete;
            }
            boolean complete = true;
            for (int i = 0; i < list.size(); i++)
                if (containsInventoryData(list.get(i))
                        && !recoverInventoryNode(list.get(i), level, output, false)) complete = false;
            return complete;
        }
        if (!(tag instanceof CompoundTag compound)) return !inventoryContext;
        if (inventoryContext && isSerializedItemStack(compound)) {
            ItemStack stack = decodeItemStack(level, compound);
            if (stack.isEmpty()) return isExplicitlyEmpty(compound);
            output.add(stack.copy());
            return true;
        }
        boolean visitedInventory = false;
        boolean complete = true;
        for (String key : compound.getAllKeys()) {
            Tag child = compound.get(key);
            boolean inventoryField = isInventoryKey(key);
            if (inventoryField) {
                visitedInventory = true;
                if (!recoverInventoryNode(child, level, output, true)) complete = false;
            } else if (containsInventoryData(child)) {
                if (!recoverInventoryNode(child, level, output, false)) complete = false;
            }
        }
        if (inventoryContext && !visitedInventory) {
            for (String key : compound.getAllKeys()) {
                if (!key.equalsIgnoreCase("Size") && !key.equalsIgnoreCase("Slots"))
                    complete = false;
            }
        }
        return complete;
    }

    private static ItemStack decodeItemStack(ServerLevel level, CompoundTag stored) {
        try {
            CompoundTag normalized = stored;
            if (!stored.contains("count", Tag.TAG_ANY_NUMERIC)
                    && stored.contains("Count", Tag.TAG_ANY_NUMERIC)) {
                normalized = stored.copy();
                normalized.putInt("count", stored.getInt("Count"));
            }
            return ItemStack.parseOptional(level.registryAccess(), normalized);
        } catch (RuntimeException error) {
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack orphanBlockItem(String id) {
        Item item = switch (id) {
            case "minecraft:furnace" -> Items.FURNACE;
            case "minecraft:blast_furnace" -> Items.BLAST_FURNACE;
            case "minecraft:smoker" -> Items.SMOKER;
            case "minecraft:chest" -> Items.CHEST;
            case "minecraft:trapped_chest" -> Items.TRAPPED_CHEST;
            case "minecraft:barrel" -> Items.BARREL;
            case "minecraft:hopper" -> Items.HOPPER;
            case "minecraft:dispenser" -> Items.DISPENSER;
            case "minecraft:dropper" -> Items.DROPPER;
            case "minecraft:brewing_stand" -> Items.BREWING_STAND;
            case "minecraft:beehive" -> Items.BEEHIVE;
            case "minecraft:decorated_pot" -> Items.DECORATED_POT;
            case "minecraft:skull" -> Items.SKELETON_SKULL;
            case "minecraft:shulker_box" -> Items.SHULKER_BOX;
            case "minecraft:lectern" -> Items.LECTERN;
            case "minecraft:chiseled_bookshelf" -> Items.CHISELED_BOOKSHELF;
            case "minecraft:jukebox" -> Items.JUKEBOX;
            default -> Items.AIR;
        };
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static boolean isExplicitlyEmpty(CompoundTag tag) {
        return tag.getString("id").equals("minecraft:air")
                || (tag.contains("count", Tag.TAG_ANY_NUMERIC) && tag.getInt("count") <= 0)
                || (tag.contains("Count", Tag.TAG_ANY_NUMERIC) && tag.getInt("Count") <= 0);
    }

    private static boolean isEmptySlotEntry(CompoundTag tag) {
        if (!tag.contains("Slot", Tag.TAG_ANY_NUMERIC)) return false;
        for (String key : tag.getAllKeys())
            if (!key.equals("Slot")) return false;
        return true;
    }

    private static boolean isSerializedItemStack(CompoundTag tag) {
        return tag.contains("id", Tag.TAG_STRING)
                && (tag.contains("count", Tag.TAG_ANY_NUMERIC)
                || tag.contains("Count", Tag.TAG_ANY_NUMERIC));
    }

    private static boolean isInventoryKey(String key) {
        String name = key.toLowerCase(java.util.Locale.ROOT);
        return name.equals("items") || name.contains("inventory")
                || name.contains("itemhandler") || name.equals("stacks")
                || name.equals("helditem") || name.equals("inv");
    }

    private static boolean containsInventoryData(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                if (isInventoryKey(key) || containsInventoryData(compound.get(key))) return true;
            }
        } else if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++)
                if (containsInventoryData(list.get(i))) return true;
        }
        return false;
    }

    private PocketedSubLevelRecycling() {}
}
