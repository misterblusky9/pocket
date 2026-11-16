package com.misterblusky9.pocket.advancement;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pocket.build.CarriedBuilds;
import com.misterblusky9.pym.api.Hosted;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.joml.Vector3d;
import com.misterblusky9.pym.api.Detached;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HatAdvancements {
    public static final TagKey<Block> HAT_PADDING =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "hat_padding"));

    private static final double HEAVY_MASS = 1000.0D;
    private static final double TALL_BLOCKS = 200.0D;
    private static final int GROWTH_CHECK_TICKS = 100;
    private static final Map<UUID, UUID> CHECKED = new HashMap<>();

    private HatAdvancements() {}

    public static void worn(ServerPlayer player, @Nullable UUID token, @Nullable ServerSubLevel hat, ItemStack headwear) {
        if (token == null) {
            CHECKED.remove(player.getUUID());
            return;
        }
        int phase = Math.floorMod(player.tickCount + player.getId(), GROWTH_CHECK_TICKS);
        if (hat != null && phase == 0 && !done(player, "doug_dimmadome") && height(hat) > TALL_BLOCKS)
            award(player, "doug_dimmadome");
        if (hat != null && phase == GROWTH_CHECK_TICKS / 3 && !done(player, "he_was_number_one") && twoBrews(hat))
            award(player, "he_was_number_one");
        if (hat == null || token.equals(CHECKED.get(player.getUUID()))) return;
        CHECKED.put(player.getUUID(), token);

        Scan scan = new Scan();
        scan.add(hat);
        for (ServerSubLevel child : Detached.children(hat)) scan.add(child);

        if (scan.tinted) award(player, "cool_shades");
        if (scan.mass > HEAVY_MASS) award(player, "heavy_is_the_head");
        if (padded(hat, BuildSites.offset(headwear))) award(player, "safety_first");
    }

    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level
                && event.getPlacedBlock().is(Blocks.LIGHTNING_ROD) && onHat(level, event.getPos()))
            award(player, "smartest_minecraft_player");
    }

    public static void started(ServerPlayer player) {
        award(player, "hats");
    }

    public static void dummied(ServerPlayer player) {
        award(player, "dummy_squared");
    }

    public static void onStruck(EntityStruckByLightningEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerSubLevel hat = CarriedBuilds.worn(player);
        if (hat != null && contains(hat, Blocks.LIGHTNING_ROD)) award(player, "darwin_award_recipient");
    }

    public static boolean contains(ServerSubLevel hat, Block block) {
        for (ServerSubLevel part : parts(hat))
            for (PlotChunkHolder holder : part.getPlot().getLoadedChunks())
                for (LevelChunkSection section : holder.getChunk().getSections()) {
                    if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(block))) continue;
                    for (int y = 0; y < 16; y++)
                        for (int x = 0; x < 16; x++)
                            for (int z = 0; z < 16; z++)
                                if (section.getBlockState(x, y, z).is(block)) return true;
                }
        return false;
    }

    private static boolean onHat(ServerLevel level, BlockPos pos) {
        if (!(Sable.HELPER.getContaining(level, pos) instanceof ServerSubLevel part)) return false;
        ServerSubLevel root = Hosted.rootOf(part);
        if (root == null) root = part;
        ServerSubLevel parent = Detached.parent(root);
        ServerSubLevel top = parent == null ? root : parent;
        return BuildSites.token(top) != null || CarriedBuilds.isHat(top);
    }

    private static List<ServerSubLevel> parts(ServerSubLevel hat) {
        List<ServerSubLevel> parts = new ArrayList<>();
        parts.add(hat);
        parts.addAll(Detached.children(hat));
        parts.addAll(Hosted.members(hat));
        return parts;
    }

    private static boolean twoBrews(ServerSubLevel hat) {
        List<FluidStack> brews = new ArrayList<>();
        for (ServerSubLevel part : parts(hat))
            for (PlotChunkHolder holder : part.getPlot().getLoadedChunks())
                for (BlockEntity blockEntity : holder.getChunk().getBlockEntities().values()) {
                    if (!(blockEntity instanceof FluidTankBlockEntity tank) || !tank.isController()
                            || tank.getWidth() < FluidTankBlockEntity.getMaxSize()
                            || tank.getHeight() < FluidTankBlockEntity.getMaxHeight()) continue;
                    FluidStack fluid = tank.getTankInventory().getFluid();
                    if (fluid.isEmpty()) continue;
                    for (FluidStack other : brews)
                        if (!FluidStack.isSameFluidSameComponents(fluid, other)) return true;
                    brews.add(fluid);
                }
        return false;
    }

    private static double height(ServerSubLevel hat) {
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        for (ServerSubLevel part : parts(hat)) {
            BoundingBox3ic bounds = part.getPlot().getBoundingBox();
            if (bounds == null) continue;
            Pose3d relative = part == hat ? null
                    : Detached.parent(part) == hat ? Detached.relative(part) : Hosted.relative(hat, part);
            for (int corner = 0; corner < 8; corner++) {
                Vector3d at = new Vector3d((corner & 1) == 0 ? bounds.minX() : bounds.maxX() + 1,
                        (corner & 2) == 0 ? bounds.minY() : bounds.maxY() + 1,
                        (corner & 4) == 0 ? bounds.minZ() : bounds.maxZ() + 1);
                if (relative != null) relative.transformPosition(at);
                low = Math.min(low, at.y);
                high = Math.max(high, at.y);
            }
        }
        return high - low;
    }

    private static boolean done(ServerPlayer player, String id) {
        AdvancementHolder advancement = player.server.getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, id));
        return advancement == null || player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static boolean padded(ServerSubLevel hat, int[] offset) {
        ServerLevel level = hat.getLevel();
        int size = ModBlocks.HAT_STAND.get().scaffoldSize();
        BlockPos origin = BuildSites.scaffoldOrigin(hat, size, offset);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < size; x++)
            for (int y = 0; y < size; y++)
                for (int z = 0; z < size; z++) {
                    int faces = (x == 0 || x == size - 1 ? 1 : 0)
                            + (y == 0 || y == size - 1 ? 1 : 0)
                            + (z == 0 || z == size - 1 ? 1 : 0);
                    if (faces != 1) continue;
                    BlockState state = level.getBlockState(pos.setWithOffset(origin, x, y, z));
                    if (!state.isAir() && !state.is(HAT_PADDING)) return false;
                }
        return true;
    }

    private static void award(ServerPlayer player, String id) {
        AdvancementHolder advancement = player.server.getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, id)
        );
        if (advancement != null) player.getAdvancements().award(advancement, "worn");
    }

    private static final class Scan {
        boolean tinted;
        double mass;

        void add(ServerSubLevel subLevel) {
            ServerLevel level = subLevel.getLevel();
            BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = bounds.minX(); x <= bounds.maxX(); x++)
                for (int y = bounds.minY(); y <= bounds.maxY(); y++)
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                        BlockState state = level.getBlockState(pos.set(x, y, z));
                        if (state.isAir() || state.is(ModBlocks.POCKET_DUMMY.get())) continue;
                        if (state.is(Blocks.TINTED_GLASS)) tinted = true;
                        double block = PhysicsBlockPropertyHelper.getMass(level, pos, state);
                        if (Double.isFinite(block) && block > 0.0D) mass += block;
                    }
        }
    }
}
