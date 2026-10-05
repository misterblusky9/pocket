package com.misterblusky9.pocket.block;

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

    public SubspaceRecyclerBlockEntity(final BlockPos pos, final BlockState state) {
        super(ModBlockEntities.SUBSPACE_RECYCLER.get(), pos, state);
        inputInv = new ItemStackHandler(1);
        outputInv = new ItemStackHandler(9);
        capability = new RecyclerInventoryHandler();
    }

    public static void registerCapabilities(final RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.SUBSPACE_RECYCLER.get(),
                (be, context) -> be.capability
        );
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new DirectBeltInputBehaviour(this));
        super.addBehaviours(behaviours);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void tickAudio() {
        super.tickAudio();

        if (getSpeed() == 0) return;
        if (inputInv.getStackInSlot(0).isEmpty()) return;

        final float pitch = Mth.clamp((Math.abs(getSpeed()) / 256f) + .45f, .85f, 1f);
        SoundScapes.play(AmbienceGroup.MILLING, worldPosition, pitch);
    }

    @Override
    public void tick() {
        super.tick();

        if (getSpeed() == 0) return;
        for (int i = 0; i < outputInv.getSlots(); i++)
            if (outputInv.getStackInSlot(i).getCount() == outputInv.getSlotLimit(i)) return;

        if (timer > 0) {
            timer -= getProcessingSpeed();

            if (level.isClientSide) {
                spawnParticles();
                return;
            }
            if (timer <= 0) process();
            return;
        }

        if (inputInv.getStackInSlot(0).isEmpty()) return;

        final RecipeWrapper inventoryIn = new RecipeWrapper(inputInv);
        if (lastRecipe == null || !lastRecipe.matches(inventoryIn, level)) {
            final Optional<RecipeHolder<MillingRecipe>> recipe = AllRecipeTypes.MILLING.find(inventoryIn, level);
            if (recipe.isEmpty()) {
                timer = 100;
                sendData();
            } else {
                lastRecipe = recipe.get().value();
                timer = lastRecipe.getProcessingDuration();
                sendData();
            }
            return;
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
        inputInv.setStackInSlot(0, ItemStack.EMPTY);
    }

    @Override
    public void destroy() {
        super.destroy();
        ItemHelper.dropContents(level, worldPosition, inputInv);
        ItemHelper.dropContents(level, worldPosition, outputInv);
    }

    private void process() {
        final RecipeWrapper inventoryIn = new RecipeWrapper(inputInv);

        if (lastRecipe == null || !lastRecipe.matches(inventoryIn, level)) {
            final Optional<RecipeHolder<MillingRecipe>> recipe = AllRecipeTypes.MILLING.find(inventoryIn, level);
            if (recipe.isEmpty()) return;
            lastRecipe = recipe.get().value();
        }

        final ItemStack stackInSlot = inputInv.getStackInSlot(0);
        final ItemStack craftingRemainingItem = stackInSlot.getCraftingRemainingItem();
        stackInSlot.shrink(1);
        inputInv.setStackInSlot(0, stackInSlot);
        lastRecipe.rollResults(level.random)
                .forEach(stack -> ItemHandlerHelper.insertItemStacked(outputInv, stack, false));
        if (!craftingRemainingItem.isEmpty())
            ItemHandlerHelper.insertItemStacked(outputInv, craftingRemainingItem, false);

        sendData();
        setChanged();
    }

    public void spawnParticles() {
        final ItemStack stackInSlot = inputInv.getStackInSlot(0);
        if (stackInSlot.isEmpty()) return;

        final ItemParticleOption data = new ItemParticleOption(ParticleTypes.ITEM, stackInSlot);
        final float angle = level.random.nextFloat() * 360;
        Vec3 offset = new Vec3(0, 0, 0.5f);
        offset = VecHelper.rotate(offset, angle, Direction.Axis.Y);
        Vec3 target = VecHelper.rotate(offset, getSpeed() > 0 ? 25 : -25, Direction.Axis.Y);

        final Vec3 center = offset.add(VecHelper.getCenterOf(worldPosition));
        target = VecHelper.offsetRandomly(target.subtract(offset), level.random, 1 / 128f);
        level.addParticle(data, center.x, center.y, center.z, target.x, target.y, target.z);
    }

    @Override
    public void write(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket) {
        compound.putInt("Timer", timer);
        compound.put("InputInventory", inputInv.serializeNBT(registries));
        compound.put("OutputInventory", outputInv.serializeNBT(registries));
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket) {
        timer = compound.getInt("Timer");
        inputInv.deserializeNBT(registries, compound.getCompound("InputInventory"));
        outputInv.deserializeNBT(registries, compound.getCompound("OutputInventory"));
        super.read(compound, registries, clientPacket);
    }

    public int getProcessingSpeed() {
        return Mth.clamp((int) Math.abs(getSpeed() / 16f), 1, 512);
    }

    private boolean canProcess(final ItemStack stack) {
        final ItemStackHandler tester = new ItemStackHandler(1);
        tester.setStackInSlot(0, stack);
        final RecipeWrapper inventoryIn = new RecipeWrapper(tester);

        if (lastRecipe != null && lastRecipe.matches(inventoryIn, level)) return true;
        return AllRecipeTypes.MILLING.find(inventoryIn, level).isPresent();
    }

    private final class RecyclerInventoryHandler extends CombinedInvWrapper {
        private RecyclerInventoryHandler() {
            super(inputInv, outputInv);
        }

        @Override
        public boolean isItemValid(final int slot, final ItemStack stack) {
            if (outputInv == getHandlerFromIndex(getIndexForSlot(slot))) return false;
            return canProcess(stack) && super.isItemValid(slot, stack);
        }

        @Override
        public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
            if (outputInv == getHandlerFromIndex(getIndexForSlot(slot))) return stack;
            if (!isItemValid(slot, stack)) return stack;
            return super.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
            if (inputInv == getHandlerFromIndex(getIndexForSlot(slot))) return ItemStack.EMPTY;
            return super.extractItem(slot, amount, simulate);
        }
    }
}
