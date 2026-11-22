package com.misterblusky9.pocket.build;

import com.misterblusky9.pocket.advancement.HatAdvancements;
import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.client.BuildMirrors;
import com.misterblusky9.pocket.item.CaseSeal;
import com.misterblusky9.pocket.item.ModDataComponents;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.pocket.PocketedSubLevelSavedData;
import com.misterblusky9.pym.api.Detached;
import com.misterblusky9.pym.api.Pym;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.index.SimSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public class BuildHostBlockEntity extends SmartBlockEntity {
    private static final String TOKEN = "pocket_hat_token";
    private static final String EPOCH = "pocket_hat_epoch";
    private static final String CREATOR = "pocket_hat_creator";
    private static final int SAVE_TICKS = 600;
    private @Nullable UUID hat;
    private int epoch;
    private @Nullable String creator;
    private @Nullable String placer;
    private boolean started;
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

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public static @Nullable UUID token(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(TOKEN) ? data.getUnsafe().getUUID(TOKEN) : null;
    }

    public static boolean hasHat(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(TOKEN);
    }

    public static int epoch(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(EPOCH) ? data.getUnsafe().getInt(EPOCH) : 0;
    }

    public static @Nullable String creator(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(CREATOR) ? data.getUnsafe().getString(CREATOR) : null;
    }

    public static void stampCreator(ItemStack stack, String creator) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putString(CREATOR, creator);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    public static void stampEpoch(ItemStack stack, int epoch) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (epoch == 0) data.remove(EPOCH);
        else data.putInt(EPOCH, epoch);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    public static void retoken(ItemStack stack, UUID token) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putUUID(TOKEN, token);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    public static void restore(ItemStack stack, UUID token, int epoch) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putUUID(TOKEN, token);
        if (epoch == 0) data.remove(EPOCH);
        else data.putInt(EPOCH, epoch);
        data.remove(CREATOR);
        BuildSites.writeOffset(data, new int[3]);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    public static void strip(ItemStack stack) {
        if (token(stack) == null) return;
        CompoundTag data = stack.get(DataComponents.CUSTOM_DATA).copyTag();
        strip(data);
        if (data.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        stack.remove(ModDataComponents.SEAL.get());
    }

    public static boolean strip(CompoundTag data) {
        if (!data.hasUUID(TOKEN)) return false;
        data.remove(TOKEN);
        data.remove(EPOCH);
        data.remove(CREATOR);
        BuildSites.writeOffset(data, new int[3]);
        return true;
    }

    public static void empty(CompoundTag saved) {
        saved.remove("Hat");
        saved.remove("Offset");
        saved.remove("Creator");
        saved.remove("Epoch");
        saved.remove("Headwear");
    }

    public @Nullable UUID getHat() { return hat; }

    public ItemStack getActivator() { return items.getStackInSlot(0); }

    public IItemHandler items() { return items; }

    public void seal(@Nullable CaseSeal seal) {
        ItemStack hat = getActivator().copy();
        PocketCaseItem.setSeal(hat, seal);
        items.setStackInSlot(0, hat);
    }

    public int[] getOffset() { return offset; }

    protected void stamp(ItemStack result) {}

    protected void animate() {}

    protected void serverTick() {}

    public Vec3 headCentre() {
        return host().anchor(getBlockState());
    }

    public Quaterniond orientation() {
        return host().orientation(getBlockState());
    }

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

    public ItemInteractionResult assemble(Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel)) return ItemInteractionResult.SUCCESS;
        if (isActive() || !player.mayBuild()) return ItemInteractionResult.FAIL;
        ItemStack held = player.getItemInHand(hand);
        if (!host().activatedBy(held)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        placer = player.getGameProfile().getName();
        started = false;
        ItemStack rest = items.insertItem(0, held, false);
        placer = null;
        if (started && player instanceof ServerPlayer serverPlayer) HatAdvancements.started(serverPlayer);
        started = false;
        if (rest == held) return ItemInteractionResult.FAIL;
        if (held.is(com.misterblusky9.pocket.item.ModItems.DUMMY_HEAD.get()) && player instanceof ServerPlayer serverPlayer)
            HatAdvancements.dummied(serverPlayer);
        player.setItemInHand(hand, rest);
        return ItemInteractionResult.SUCCESS;
    }

    private void changed() {
        if (!(level instanceof ServerLevel server)) return;
        ItemStack stack = getActivator();
        UUID held = token(stack);
        if (hat != null && (stack.isEmpty() || held != null && !hat.equals(held))) unmount(server);
        if (hat == null && !stack.isEmpty()) claim(server);
        sync();
    }

    private void claim(ServerLevel server) {
        ItemStack stack = getActivator();
        UUID token = token(stack);
        if (token != null && (BuildSites.ownedElsewhere(token, server, worldPosition)
                || BuildSites.holds(token, server, worldPosition))) return;
        if (token != null && BuildSites.live(token) == null && !BuildSites.current(server, token, stack)
                && CarriedBuilds.holds(token)) {
            strip(stack);
            token = null;
        }
        boolean reopened = token != null && BuildSites.live(token) == null;
        ServerSubLevel child = token == null ? null : BuildSites.open(server, token);
        if (child == null) {
            reopened = false;
            if (token != null && BuildSites.pending(server, token)) return;
            token = UUID.randomUUID();
            child = BuildSites.create(server);
            if (child == null) return;
            creator = placer;
            started = placer != null;
        } else {
            creator = creator(stack);
        }
        hat = token;
        epoch = reopened ? BuildSites.claim(server, token) : BuildSites.epoch(server, token);
        offset = BuildSites.readOffset(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
        mount(server, child);
        label(child);
        setActive(true);
        server.playSound(null, worldPosition, SimSoundEvents.SIMULATED_CONTRAPTION_MOVES.event(),
                SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private void unmount(ServerLevel server) {
        UUID token = hat;
        hat = null;
        creator = null;
        ServerSubLevel child = BuildSites.live(token);
        if (child != null) {
            scaffold(server, child, false);
            if (BuildSites.hasBuild(child)) {
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
        boolean unsealed = false;
        if (BuildSites.hasBuild(child)) {
            data.putUUID(TOKEN, hat);
            if (epoch == 0) data.remove(EPOCH);
            else data.putInt(EPOCH, epoch);
            if (creator == null) data.remove(CREATOR);
            else data.putString(CREATOR, creator);
            BuildSites.writeOffset(data, offset);
        } else {
            data.remove(TOKEN);
            data.remove(EPOCH);
            data.remove(CREATOR);
            BuildSites.writeOffset(data, new int[3]);
            unsealed = stack.remove(ModDataComponents.SEAL.get()) != null;
        }
        CustomData labelled = data.isEmpty() ? null : CustomData.of(data);
        if (labelled == null ? !stack.has(DataComponents.CUSTOM_DATA)
                : labelled.equals(stack.get(DataComponents.CUSTOM_DATA))) return unsealed;
        if (labelled == null) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, labelled);
        return true;
    }

    public static ItemStack plain(ItemStack stack) {
        if (token(stack) == null) return stack;
        ItemStack plain = stack.copy();
        strip(plain);
        return plain;
    }

    public static ItemStack withHatOf(ItemStack source, ItemStack target) {
        ItemStack result = target.copyWithCount(1);
        CompoundTag data = result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putUUID(TOKEN, token(source));
        if (epoch(source) != 0) data.putInt(EPOCH, epoch(source));
        if (creator(source) != null) data.putString(CREATOR, creator(source));
        BuildSites.writeOffset(data, BuildSites.offset(source));
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return result;
    }

    private void mount(ServerLevel server, ServerSubLevel child) {
        BuildSites.track(hat, child, worldPosition);
        BuildSites.follow(hat, child, server, worldPosition, offset);
        scaffold(server, child, true);
    }

    private @Nullable ServerSubLevel live(ServerLevel server) {
        if (hat == null || getActivator().isEmpty()) return null;
        if (BuildSites.ownedElsewhere(hat, server, worldPosition)) {
            hat = null;
            setActive(false);
            return null;
        }
        ServerSubLevel child = BuildSites.live(hat);
        if (child != null && child.getLevel() != server) {
            BuildSites.close(hat);
            child = null;
        }
        if (child == null) {
            if (BuildSites.holds(hat, server, worldPosition)) {
                hat = null;
                setActive(false);
                return null;
            }
            if (!BuildSites.current(server, hat, getActivator()) && CarriedBuilds.holds(hat)) {
                strip(getActivator());
                hat = null;
                sync();
                return null;
            }
            child = BuildSites.open(server, hat);
            if (child == null) {
                if (BuildSites.pending(server, hat)) return null;
                hat = UUID.randomUUID();
                child = BuildSites.create(server);
                if (child == null) return null;
                epoch = 0;
                creator = null;
            } else {
                epoch = BuildSites.claim(server, hat);
                creator = creator(getActivator());
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
        if (!BuildSites.hasBuild(child) || !ready.test(child)) {
            scaffold(server, child, true);
            return null;
        }
        // Store the build's original position within this stand's grid before the
        // detached/hosted -> physical conversion discards the stand association.
        PocketCaseItem.rememberHatAnchor(child, BuildSites.scaffoldOrigin(child, host().scaffoldSize(), offset));
        ServerSubLevel released = BuildSites.releaseToPhysics(hat, child, PocketCaseItem.POCKETABLE_SCALE);
        if (released == null) {
            ServerSubLevel restored = BuildSites.live(hat);
            if (restored != null) scaffold(server, restored, true);
            return null;
        }
        PocketCaseItem.copyHatAnchor(child, released);
        hat = UUID.randomUUID();
        epoch = 0;
        creator = null;
        offset = new int[3];
        ServerSubLevel fresh = BuildSites.create(server);
        if (fresh != null) {
            mount(server, fresh);
            label(fresh);
        }
        sync();
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
        if (token == null) return;
        ServerLevel stored = BuildSites.storedIn(server, token);
        if (stored != null) PocketedSubLevelSavedData.getOrLoad(stored).remember(token, result, server.registryAccess());
        PocketedSubLevelSavedData.getOrLoad(server).commitCapture(server, token, holder);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BuildHostBlockEntity host) {
        host.tick();
        if (!(level instanceof ServerLevel server)) return;
        host.serverTick();
        if (host.hat == null && !host.getActivator().isEmpty()) host.claim(server);
        ServerSubLevel child = host.live(server);
        if (child == null) return;
        BuildSites.follow(host.hat, child, server, pos, host.offset);
        BuildSites.settleHosting(host.hat);
        child = BuildSites.live(host.hat);
        if (child == null) return;
        BuildSites.prune(server, host.hat, child);
        if (host.label(child)) host.sync();
        if ((server.getGameTime() + pos.asLong()) % SAVE_TICKS == 0) BuildSites.save(host.hat);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, BuildHostBlockEntity host) {
        host.tick();
        host.animate();
        if (host.hat != null && !host.getActivator().isEmpty() && BuildMirrors.current(host.hat, host.getActivator()))
            BuildMirrors.stand(host.hat, level, pos, host.offset);
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
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) tag.putBoolean("Active", !getActivator().isEmpty());
        if (hat != null) tag.putUUID("Hat", hat);
        if (offset[0] != 0 || offset[1] != 0 || offset[2] != 0) tag.putIntArray("Offset", offset);
        if (creator != null) tag.putString("Creator", creator);
        if (epoch != 0) tag.putInt("Epoch", epoch);
        if (!getActivator().isEmpty()) tag.put("Headwear", getActivator().save(registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        hat = tag.hasUUID("Hat") ? tag.getUUID("Hat") : null;
        int[] saved = tag.getIntArray("Offset");
        offset = saved.length == 3 ? saved : new int[3];
        creator = tag.contains("Creator") ? tag.getString("Creator") : null;
        epoch = tag.getInt("Epoch");
        items.setStackInSlot(0, tag.contains("Headwear", Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(registries, tag.getCompound("Headwear")) : ItemStack.EMPTY);
    }

}
