package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.pocket.PocketedSubLevelRecycling;
import com.misterblusky9.pocket.pocket.PocketedSubLevelSavedData;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.sound.SoundScapes;
import com.simibubi.create.foundation.sound.SoundScapes.AmbienceGroup;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

import java.util.List;
import java.util.Optional;

public final class SubspaceRecyclerBlockEntity extends KineticBlockEntity implements Clearable {
    public final ItemStackHandler inputInv;
    public final ItemStackHandler outputInv;
    public final IItemHandler capability;
    public int timer;

    private MillingRecipe lastRecipe;

    private int recycleStage;
    private UUID recycleToken;
    private String recycleDimension = "";
    private final ArrayList<ItemStack> backlog = new ArrayList<>();
    private String lastError = "";
    private String lastSalvageReport = "";
    private int pendingSalvageWarnings;
    private boolean journalCorrupt;

    public boolean isPreparing() { return recycleStage == 1; }

    public String inspectionStatus() {
        if (journalCorrupt) return "Output journal corrupt: keep the machine intact and inspect the server log";
        if (recycleStage == 1)
            return lastError.isEmpty() ? "Finishing pocket retirement" : "Transaction paused: " + lastError;
        if (recycleStage == 2)
            return backlog.isEmpty() ? "Output ready for extraction" :
                    "Waiting for output space: " + backlog.size() + " buffered stacks";
        if (inputInv.getStackInSlot(0).isEmpty()) return lastSalvageReport.isEmpty()
                ? (getSpeed() == 0 ? "No rotational power" : "No input pocket or milling item")
                : lastSalvageReport;
        if (getSpeed() == 0) return "No rotational power";
        if (!lastError.isEmpty()) return "Cannot process: " + lastError;
        if (PocketCaseItem.isUnique(inputInv.getStackInSlot(0)))
            return "Pocket in processing queue";
        return "Ordinary milling input in processing queue";
    }

    private static final int MAX_BUFFERED_STACKS = 8192;

    public SubspaceRecyclerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUBSPACE_RECYCLER.get(), pos, state);
        inputInv = new ItemStackHandler(1) {
            @Override
            protected void onContentsChanged(int slot) {
                if (recycleStage == 0) lastError = "";
                setChanged();
                if (level != null && !level.isClientSide) sendData();
            }
        };
        outputInv = new ItemStackHandler(9) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                if (level != null && !level.isClientSide) sendData();
            }
        };
        capability = new RecyclerInventoryHandler();
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.SUBSPACE_RECYCLER.get(),
                (be, context) -> be.capability
        );
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new DirectBeltInputBehaviour(this));
        super.addBehaviours(behaviours);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void tickAudio() {
        super.tickAudio();

        if (getSpeed() == 0) return;
        if (inputInv.getStackInSlot(0).isEmpty()) return;

        float pitch = Mth.clamp((Math.abs(getSpeed()) / 256f) + .45f, .85f, 1f);
        SoundScapes.play(AmbienceGroup.MILLING, worldPosition, pitch);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;
        if (level.isClientSide) {
            if (getSpeed() != 0 && timer > 0) {
                timer -= getProcessingSpeed();
                spawnParticles();
            }
            return;
        }
        if (!(level instanceof ServerLevel server)) return;
        if (journalCorrupt) {
            recordProblem("Recycler output journal is corrupt; automatic processing is locked");
            return;
        }

        if (recycleStage == 1) {
            commitJournal(server);
            return;
        }
        if (recycleStage == 2) {
            flushBacklog();
            if (!backlog.isEmpty()) return;
            recycleStage = 0;
            recycleToken = null;
            recycleDimension = "";
            setChanged();
        }

        if (getSpeed() == 0 || inputInv.getStackInSlot(0).isEmpty()) return;
        if (timer > 0) {
            timer -= getProcessingSpeed();
            if (timer <= 0) process(server);
            return;
        }
        ItemStack incoming = inputInv.getStackInSlot(0);
        if (PocketCaseItem.isUnique(incoming)) {
            timer = 100;
            sendData();
            return;
        }
        RecipeWrapper inventoryIn = new RecipeWrapper(inputInv);
        if (lastRecipe == null || !lastRecipe.matches(inventoryIn, level)) {
            Optional<RecipeHolder<MillingRecipe>> recipe = AllRecipeTypes.MILLING.find(inventoryIn, level);
            if (recipe.isEmpty()) return;
            lastRecipe = recipe.get().value();
        }
        timer = lastRecipe.getProcessingDuration();
        sendData();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        invalidateCapabilities();
    }

    @Override
    public void clearContent() {
        if (recycleStage == 0) inputInv.setStackInSlot(0, ItemStack.EMPTY);
    }

    @Override
    public void destroy() {
        super.destroy();
        if (level == null || level.isClientSide) return;
        boolean carrierStillLive = recycleStage != 1
                || stagedSource((ServerLevel) level) == null
                || stagedPayloadExists((ServerLevel) level);
        if (recycleStage == 0 || (recycleStage == 1 && carrierStillLive))
            ItemHelper.dropContents(level, worldPosition, inputInv);
        ItemHelper.dropContents(level, worldPosition, outputInv);
        if (recycleStage == 2 || (recycleStage == 1 && !carrierStillLive))
            for (ItemStack stack : backlog) Block.popResource(level, worldPosition, stack.copy());
    }

    private void process(ServerLevel server) {
        ItemStack incoming = inputInv.getStackInSlot(0);
        if (PocketCaseItem.isUnique(incoming)) {
            processPocket(server, incoming);
            return;
        }
        RecipeWrapper inventoryIn = new RecipeWrapper(inputInv);
        if (lastRecipe == null || !lastRecipe.matches(inventoryIn, level)) {
            Optional<RecipeHolder<MillingRecipe>> recipe = AllRecipeTypes.MILLING.find(inventoryIn, level);
            if (recipe.isEmpty()) return;
            lastRecipe = recipe.get().value();
        }
        ArrayList<ItemStack> generated = new ArrayList<>(lastRecipe.rollResults(level.random));
        ItemStack craftingRemainder = incoming.getCraftingRemainingItem();
        if (!craftingRemainder.isEmpty()) generated.add(craftingRemainder);
        ArrayList<ItemStack> packed = withBacklog(generated);
        if (packed.size() > MAX_BUFFERED_STACKS) {
            timer = 100;
            return;
        }
        incoming.shrink(1);
        inputInv.setStackInSlot(0, incoming);
        backlog.clear();
        backlog.addAll(packed);
        recycleStage = 2;
        setChanged();
        sendData();
    }

    private void processPocket(ServerLevel server, ItemStack carrier) {
        try {
            PocketedSubLevelRecycling.Plan plan = PocketedSubLevelRecycling.plan(server, carrier);
            ArrayList<ItemStack> packed = compact(plan.drops());
            if (packed.size() > MAX_BUFFERED_STACKS) {
                recordProblem("Recycling would exceed the " + MAX_BUFFERED_STACKS + "-stack buffer");
                timer = 200;
                return;
            }
            pendingSalvageWarnings = plan.salvageWarnings();
            backlog.clear();
            backlog.addAll(packed);
            recycleToken = plan.token();
            recycleDimension = plan.source().dimension().location().toString();
            recycleStage = 1;
            setChanged();
            server.save(null, false, false);
            commitJournal(server);
        } catch (RuntimeException e) {
            recordProblem("Cannot recycle this pocket: " + e.getMessage());
            if (recycleStage == 1 && stagedPayloadExists(server)) {
                recycleStage = 0;
                recycleToken = null;
                recycleDimension = "";
                backlog.clear();
                pendingSalvageWarnings = 0;
            }
            timer = 200;
            setChanged();
        }
    }

    private ServerLevel stagedSource(ServerLevel server) {
        ResourceLocation sourceId = ResourceLocation.tryParse(recycleDimension);
        return sourceId == null ? null : server.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, sourceId));
    }

    private boolean stagedPayloadExists(ServerLevel server) {
        ServerLevel source = stagedSource(server);
        return recycleToken != null && source != null
                && PocketedSubLevelSavedData.getOrLoad(source).contains(recycleToken);
    }

    private void commitJournal(ServerLevel server) {
        if (recycleStage != 1 || recycleToken == null) return;
        if (journalCorrupt || backlog.isEmpty()) {
            recordProblem("Corrupt or empty recycle journal: payload is locked for recovery");
            return;
        }
        ServerLevel source = stagedSource(server);
        if (source == null) {
            recordProblem("The source dimension is unavailable; recycling is paused");
            return;
        }
        PocketedSubLevelSavedData storage = PocketedSubLevelSavedData.getOrLoad(source);
        if (!storage.contains(recycleToken) && !storage.wasDeployed(recycleToken)) {
            recordProblem("Saved pocket record is missing; the output journal is locked");
            return;
        }
        try {
            if (storage.contains(recycleToken)) storage.remove(recycleToken);
            storage.markDeployed(recycleToken);
            storage.commitDeploy(source, server, recycleToken, null);
            inputInv.setStackInSlot(0, ItemStack.EMPTY);
            timer = 0;
            recycleStage = 2;
            lastSalvageReport = pendingSalvageWarnings == 0
                    ? "Last pocket salvaged without warnings"
                    : "Last pocket salvaged with " + pendingSalvageWarnings
                            + " best-effort warnings; see server log for lost/inferred data";
            pendingSalvageWarnings = 0;
            setChanged();
            server.save(null, false, false);
            PocketTrace.debug("[Recycler] committed token={} outputs={} pos={}",
                    recycleToken, backlog.size(), worldPosition);
        } catch (RuntimeException e) {
            recordProblem("Pocket retirement could not be saved: " + e.getMessage());
        }
    }

    private static ArrayList<ItemStack> compact(List<ItemStack> stacks) {
        ArrayList<ItemStack> packed = new ArrayList<>();
        Map<Integer, ArrayList<ItemStack>> partial = new HashMap<>();
        for (ItemStack original : stacks) {
            if (original == null || original.isEmpty()) continue;
            ItemStack remaining = original.copy();
            int hash = ItemStack.hashItemAndComponents(remaining);
            ArrayList<ItemStack> candidates = partial.computeIfAbsent(hash, ignored -> new ArrayList<>());
            for (int i = candidates.size() - 1; i >= 0 && !remaining.isEmpty(); i--) {
                ItemStack stored = candidates.get(i);
                if (!ItemStack.isSameItemSameComponents(stored, remaining)) continue;
                int moved = Math.min(stored.getMaxStackSize() - stored.getCount(), remaining.getCount());
                if (moved > 0) { stored.grow(moved); remaining.shrink(moved); }
                if (stored.getCount() == stored.getMaxStackSize()) candidates.remove(i);
            }
            while (!remaining.isEmpty()) {
                int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                ItemStack placed = remaining.copyWithCount(moved);
                packed.add(placed);
                if (moved < placed.getMaxStackSize()) candidates.add(placed);
                remaining.shrink(moved);
            }
        }
        return packed;
    }

    private ArrayList<ItemStack> withBacklog(List<ItemStack> incoming) {
        ArrayList<ItemStack> combined = new ArrayList<>(backlog);
        combined.addAll(incoming);
        return compact(combined);
    }

    private void flushBacklog() {
        if (backlog.isEmpty()) return;
        boolean changed = false;
        while (!backlog.isEmpty()) {
            ItemStack current = backlog.get(0);
            ItemStack leftover = ItemHandlerHelper.insertItemStacked(outputInv, current.copy(), false);
            if (leftover.getCount() == current.getCount()) break;
            backlog.remove(0);
            if (!leftover.isEmpty()) backlog.add(0, leftover);
            changed = true;
        }
        if (changed) { setChanged(); sendData(); }
    }

    private void recordProblem(String message) {
        if (!message.equals(lastError)) {
            lastError = message;
            PocketTrace.logger().warn("[Recycler] {} at {}", message, worldPosition);
        }
    }

    public void spawnParticles() {
        ItemStack stackInSlot = inputInv.getStackInSlot(0);
        if (stackInSlot.isEmpty()) return;

        ItemParticleOption data = new ItemParticleOption(ParticleTypes.ITEM, stackInSlot);
        float angle = level.random.nextFloat() * 360;
        Vec3 offset = new Vec3(0, 0, 0.5f);
        offset = VecHelper.rotate(offset, angle, Direction.Axis.Y);
        Vec3 target = VecHelper.rotate(offset, getSpeed() > 0 ? 25 : -25, Direction.Axis.Y);

        Vec3 center = offset.add(VecHelper.getCenterOf(worldPosition));
        target = VecHelper.offsetRandomly(target.subtract(offset), level.random, 1 / 128f);
        level.addParticle(data, center.x, center.y, center.z, target.x, target.y, target.z);
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.putInt("Timer", timer);
        compound.putInt("RecycleStage", recycleStage);
        if (recycleToken != null) compound.putUUID("RecycleToken", recycleToken);
        compound.putString("RecycleDimension", recycleDimension);
        compound.putString("LastSalvageReport", lastSalvageReport);
        compound.putInt("PendingSalvageWarnings", pendingSalvageWarnings);
        if (!clientPacket) {
            ListTag buffered = new ListTag();
            for (ItemStack stack : backlog) buffered.add(stack.save(registries));
            compound.put("RecycleBacklog", buffered);
            compound.putInt("RecycleBacklogCount", backlog.size());
        }
        compound.put("InputInventory", inputInv.serializeNBT(registries));
        compound.put("OutputInventory", outputInv.serializeNBT(registries));
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        timer = compound.getInt("Timer");
        recycleStage = compound.getInt("RecycleStage");
        recycleToken = compound.hasUUID("RecycleToken") ? compound.getUUID("RecycleToken") : null;
        recycleDimension = compound.getString("RecycleDimension");
        lastSalvageReport = compound.getString("LastSalvageReport");
        pendingSalvageWarnings = compound.getInt("PendingSalvageWarnings");
        if (!clientPacket) {
            backlog.clear();
            ListTag buffered = compound.getList("RecycleBacklog", Tag.TAG_COMPOUND);
            for (int i = 0; i < buffered.size(); ++i) {
                ItemStack stack = ItemStack.parseOptional(registries, buffered.getCompound(i));
                if (!stack.isEmpty()) backlog.add(stack);
            }
            journalCorrupt = (recycleStage == 1 || recycleStage == 2)
                    && (backlog.size() != compound.getInt("RecycleBacklogCount"));
        }
        inputInv.deserializeNBT(registries, compound.getCompound("InputInventory"));
        outputInv.deserializeNBT(registries, compound.getCompound("OutputInventory"));
        super.read(compound, registries, clientPacket);
    }

    public int getProcessingSpeed() {
        return Mth.clamp((int) Math.abs(getSpeed() / 16f), 1, 512);
    }

    private boolean canProcess(ItemStack stack) {
        ItemStackHandler tester = new ItemStackHandler(1);
        tester.setStackInSlot(0, stack);
        RecipeWrapper inventoryIn = new RecipeWrapper(tester);

        if (lastRecipe != null && lastRecipe.matches(inventoryIn, level)) return true;
        return PocketCaseItem.isUnique(stack)
                || AllRecipeTypes.MILLING.find(inventoryIn, level).isPresent();
    }

    private final class RecyclerInventoryHandler extends CombinedInvWrapper {
        private RecyclerInventoryHandler() {
            super(inputInv, outputInv);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (outputInv == getHandlerFromIndex(getIndexForSlot(slot))) return false;
            return recycleStage == 0 && canProcess(stack) && super.isItemValid(slot, stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (outputInv == getHandlerFromIndex(getIndexForSlot(slot))) return stack;
            if (!isItemValid(slot, stack)) return stack;
            return super.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (inputInv == getHandlerFromIndex(getIndexForSlot(slot))) return ItemStack.EMPTY;
            return super.extractItem(slot, amount, simulate);
        }
    }
}
