package com.misterblusky9.pocket.build;

import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.client.BuildMirrors;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.pocket.PocketedSubLevelSavedData;
import com.misterblusky9.pym.api.Detached;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.internal.physics.ScaledColliderRebuildQueue;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.simulated_team.simulated.index.SimSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.Predicate;

public class BuildHostBlockEntity extends BlockEntity {
    private static final String TOKEN = "pocket_hat_token";
    private static final int SAVE_TICKS = 600;
    private @Nullable UUID hat;
    private int[] offset = new int[3];
    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return host().activatedBy(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            changed();
        }
    };

    public BuildHostBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static @Nullable UUID token(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(TOKEN) ? data.copyTag().getUUID(TOKEN) : null;
    }

    public @Nullable UUID getHat() { return hat; }

    public ItemStack getActivator() { return items.getStackInSlot(0); }

    public IItemHandler items() { return items; }

    public int[] getOffset() { return offset; }

    protected void stamp(ItemStack result) {}

    public boolean isActive() {
        return !getActivator().isEmpty() || getBlockState().getValue(BuildHostBlock.ACTIVE);
    }

    private BuildHostBlock host() {
        return (BuildHostBlock) getBlockState().getBlock();
    }

    private void setActive(boolean active) {
        if (!(level instanceof ServerLevel server)) return;
        BlockState current = server.getBlockState(worldPosition);
        if (current.getBlock() instanceof BuildHostBlock && current.getValue(BuildHostBlock.ACTIVE) != active)
            server.setBlock(worldPosition, current.setValue(BuildHostBlock.ACTIVE, active), 3);
        sync();
    }

    private void scaffold(ServerLevel level, ServerSubLevel child, boolean create) {
        BuildHostBlock host = host();
        int size = host.scaffoldSize();
        PocketDummyBlock.beginEdit();
        try {
            BlockPos origin = BuildSites.scaffoldOrigin(child, size, offset);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    for (int z = 0; z < size; z++) {
                        BlockPos pos = origin.offset(x, y, z);
                        BlockState previous = level.getBlockState(pos);
                        boolean shell = x == 0 || y == 0 || z == 0
                                || x == size - 1 || y == size - 1 || z == size - 1;
                        if (create) {
                            if (!shell && previous.isAir()) level.setBlock(pos, host.scaffold(), 2);
                        } else if (previous.is(ModBlocks.POCKET_DUMMY.get())) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            }
            child.getPlot().updateBoundingBox();
        } finally {
            PocketDummyBlock.endEdit();
        }
    }

    private static boolean hasBuild(ServerSubLevel child) {
        if (!Detached.children(child).isEmpty()) return true;
        for (var holder : child.getPlot().getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            for (LevelChunkSection section : chunk.getSections()) {
                if (section.hasOnlyAir()) continue;
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (!state.isAir() && !state.is(ModBlocks.POCKET_DUMMY.get())) return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public ItemInteractionResult assemble(Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel)) return ItemInteractionResult.SUCCESS;
        if (isActive() || !player.mayBuild()) return ItemInteractionResult.FAIL;
        ItemStack held = player.getItemInHand(hand);
        if (!host().activatedBy(held)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        ItemStack rest = items.insertItem(0, held, false);
        if (rest == held) return ItemInteractionResult.FAIL;
        player.setItemInHand(hand, rest);
        return ItemInteractionResult.SUCCESS;
    }

    private void changed() {
        if (!(level instanceof ServerLevel server)) return;
        ItemStack stack = getActivator();
        UUID held = token(stack);
        if (hat != null && (stack.isEmpty() || held != null && !hat.equals(held))) unmount(server);
        if (hat == null && !stack.isEmpty()) {
            UUID token = held;
            ServerSubLevel child = token == null ? null : BuildSites.open(server, token);
            if (child == null) {
                token = UUID.randomUUID();
                child = BuildSites.create(server);
                if (child == null) return;
            }
            hat = token;
            offset = BuildSites.readOffset(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
            mount(server, child);
            label(child);
            setActive(true);
            server.playSound(null, worldPosition, SimSoundEvents.SIMULATED_CONTRAPTION_MOVES.event(),
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        sync();
    }

    private void unmount(ServerLevel server) {
        UUID token = hat;
        hat = null;
        ServerSubLevel child = BuildSites.live(token);
        if (child != null) {
            scaffold(server, child, false);
            if (hasBuild(child)) {
                BuildSites.close(token);
            } else {
                BuildSites.forget(token);
                Detached.discard(child);
                PocketedSubLevelSavedData.getOrLoad(server).remove(token);
            }
        }
        setActive(false);
        server.playSound(null, worldPosition, SimSoundEvents.SIMULATED_CONTRAPTION_STOPS.event(),
                SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private boolean label(ServerSubLevel child) {
        ItemStack stack = getActivator();
        if (stack.isEmpty() || hat == null) return false;
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (hasBuild(child)) {
            data.putUUID(TOKEN, hat);
            BuildSites.writeOffset(data, offset);
        } else {
            data.remove(TOKEN);
            BuildSites.writeOffset(data, new int[3]);
        }
        CustomData labelled = data.isEmpty() ? null : CustomData.of(data);
        if (labelled == null ? !stack.has(DataComponents.CUSTOM_DATA)
                : labelled.equals(stack.get(DataComponents.CUSTOM_DATA))) return false;
        if (labelled == null) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, labelled);
        return true;
    }

    public static ItemStack plain(ItemStack stack) {
        if (token(stack) == null) return stack;
        ItemStack plain = stack.copy();
        CompoundTag data = plain.get(DataComponents.CUSTOM_DATA).copyTag();
        data.remove(TOKEN);
        BuildSites.writeOffset(data, new int[3]);
        if (data.isEmpty()) plain.remove(DataComponents.CUSTOM_DATA);
        else plain.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return plain;
    }

    private void mount(ServerLevel server, ServerSubLevel child) {
        BuildSites.track(hat, child, worldPosition);
        BuildSites.follow(hat, child, server, worldPosition, offset);
        scaffold(server, child, true);
    }

    private @Nullable ServerSubLevel live(ServerLevel server) {
        if (hat == null || getActivator().isEmpty()) return null;
        ServerSubLevel child = BuildSites.live(hat);
        if (child != null && child.getLevel() != server) {
            BuildSites.close(hat);
            child = null;
        }
        if (child == null) {
            child = BuildSites.open(server, hat);
            if (child == null) {
                hat = UUID.randomUUID();
                child = BuildSites.create(server);
                if (child == null) return null;
            }
            mount(server, child);
            label(child);
            sync();
        } else {
            BuildSites.track(hat, child, worldPosition);
        }
        return child;
    }

    public @Nullable ServerSubLevel release(Player player, Predicate<ServerSubLevel> ready) {
        if (!(level instanceof ServerLevel server) || !player.mayBuild()) return null;
        ServerSubLevelContainer container = SubLevelContainer.getContainer(server);
        ServerSubLevel child = live(server);
        if (container == null || child == null) return null;
        scaffold(server, child, false);
        if (!hasBuild(child) || !ready.test(child)) {
            scaffold(server, child, true);
            return null;
        }
        UUID old = hat;
        for (ServerSubLevel rider : Detached.children(child)) Detached.release(rider);
        CompoundTag payload = BuildSites.payload(child);
        BuildSites.forget(old);
        Detached.close(child);
        SubLevelData data = SubLevelSerializer.fromData(payload);
        ServerSubLevel released = data == null ? null : SubLevelSerializer.fullyLoad(server, data);
        if (released == null) {
            ServerSubLevel back = Detached.open(server, payload.getUUID("uuid"), payload.getCompound("plot"));
            if (back != null) mount(server, back);
            return null;
        }
        PocketedSubLevelSavedData.getOrLoad(server).remove(old);
        boolean shrunk = Pym.resize().request(released).scaleTo(PocketCaseItem.POCKETABLE_SCALE)
                .immediate().submit().accepted();

        hat = UUID.randomUUID();
        offset = new int[3];
        ServerSubLevel fresh = BuildSites.create(server);
        if (fresh != null) {
            mount(server, fresh);
            label(fresh);
        }
        sync();
        if (!shrunk) return null;
        released.updateMergedMassData(1.0F);
        container.physicsSystem().getPipeline().onStatsChanged(released);
        ScaledColliderRebuildQueue.mark(released);
        server.playSound(null, worldPosition, SimSoundEvents.SIMULATED_CONTRAPTION_STOPS.event(),
                SoundSource.BLOCKS, 1.0F, 1.0F);
        return released;
    }

    public InteractionResult take(Player player) {
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (getActivator().isEmpty() || !player.mayBuild()) return InteractionResult.PASS;
        ItemStack result = items.extractItem(0, 1, false);
        if (!player.getInventory().add(result)) player.drop(result, false);
        commit(server, result, player instanceof ServerPlayer serverPlayer ? serverPlayer : null);
        return InteractionResult.SUCCESS;
    }

    private static void commit(ServerLevel server, ItemStack result, @Nullable ServerPlayer holder) {
        UUID token = token(result);
        if (token != null) PocketedSubLevelSavedData.getOrLoad(server).commitCapture(server, token, holder);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BuildHostBlockEntity host) {
        if (!(level instanceof ServerLevel server)) return;
        ServerSubLevel child = host.live(server);
        if (child == null) return;
        BuildSites.follow(host.hat, child, server, pos, host.offset);
        BuildSites.prune(server, host.hat, child);
        if (host.label(child)) host.sync();
        if ((server.getGameTime() + pos.asLong()) % SAVE_TICKS == 0) BuildSites.save(host.hat);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, BuildHostBlockEntity host) {
        if (host.hat != null && !host.getActivator().isEmpty()) BuildMirrors.stand(host.hat, level, pos, host.offset);
    }

    public void nudge(Player player, int x, int y, int z) {
        if (!(level instanceof ServerLevel server) || !player.mayBuild()
                || player.distanceToSqr(worldPosition.getCenter()) > 64.0) return;
        ServerSubLevel child = live(server);
        if (child == null) return;
        int[] moved = {clamp(offset[0] + x), clamp(offset[1] + y), clamp(offset[2] + z)};
        if (Arrays.equals(moved, offset)) return;
        scaffold(server, child, false);
        offset = moved;
        scaffold(server, child, true);
        BuildSites.follow(hat, child, server, worldPosition, offset);
        label(child);
        sync();
    }

    private static int clamp(int value) {
        return Math.max(-BuildSites.REACH, Math.min(BuildSites.REACH, value));
    }

    public void moved() {
        if (level instanceof ServerLevel server) live(server);
    }

    public void removeChild() {
        if (!(level instanceof ServerLevel server) || getActivator().isEmpty()) return;
        ItemStack dropped = items.extractItem(0, 1, false);
        Containers.dropItemStack(server, worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, dropped);
        commit(server, dropped, null);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel && hat != null) BuildSites.unload(hat);
    }

    protected void sync() {
        if (level instanceof ServerLevel && hat != null) stamp(getActivator());
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (hat != null) tag.putUUID("Hat", hat);
        if (offset[0] != 0 || offset[1] != 0 || offset[2] != 0) tag.putIntArray("Offset", offset);
        if (!getActivator().isEmpty()) tag.put("Headwear", getActivator().save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hat = tag.hasUUID("Hat") ? tag.getUUID("Hat") : null;
        int[] saved = tag.getIntArray("Offset");
        offset = saved.length == 3 ? saved : new int[3];
        items.setStackInSlot(0, tag.contains("Headwear", Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(registries, tag.getCompound("Headwear")) : ItemStack.EMPTY);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.putBoolean("Active", !getActivator().isEmpty());
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
