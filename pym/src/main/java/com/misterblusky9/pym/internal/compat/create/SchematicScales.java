package com.misterblusky9.pym.internal.compat.create;

import com.misterblusky9.pym.api.Detached;
import com.misterblusky9.pym.api.Hosted;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.SchematicScale;
import com.misterblusky9.pym.api.spi.SchematicListener;
import com.misterblusky9.pym.internal.compat.create.mixin.SchematicPrinterErrorAccessor;
import com.misterblusky9.pym.internal.extension.PymExtensions;
import com.mojang.logging.LogUtils;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.schematics.SchematicItem;
import com.simibubi.create.content.schematics.SchematicPrinter;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.schematics.SchematicLevelExtension;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.schematics.SchematicPrinterExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableNBTUtils;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SchematicScales {
    public static final String SUB_LEVELS = "sub_levels";
    public static final String KEY = SchematicScale.KEY;
    private static final ThreadLocal<SubLevelSchematicSerializationContext> SAVING = new ThreadLocal<>();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Placing> PLACING = new ThreadLocal<>();
    private static final ThreadLocal<Double> GRID = new ThreadLocal<>();
    private static final ThreadLocal<Double> INSTANT = new ThreadLocal<>();
    private static final String FINE = "pym_schematic_fine";
    private static final double TOLERANCE = 0.05D;
    private static final Map<UUID, Double> KNOWN = new ConcurrentHashMap<>();
    public static final UUID BODY = new UUID(0x50594D5343484DL, 0x424F4459L);

    public static double of(UUID subLevel) {
        return KNOWN.getOrDefault(subLevel, ScaleBounds.FULL);
    }

    public static void contextChanging(SubLevelSchematicSerializationContext next) {
        if (next != null) {
            if (next.getType() == SubLevelSchematicSerializationContext.Type.SAVE) SAVING.set(next);
            return;
        }
        SubLevelSchematicSerializationContext current = SubLevelSchematicSerializationContext.getCurrentContext();
        if (current != null && current.getType() == SubLevelSchematicSerializationContext.Type.PLACE) place(current);
    }

    public static void read(CompoundTag tag) {
        ListTag entries = tag.getList(SUB_LEVELS, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID("uuid") || !entry.contains(KEY, Tag.TAG_ANY_NUMERIC)) continue;
            double scale = entry.getDouble(KEY);
            if (ScaleBounds.isValid(scale)) KNOWN.put(entry.getUUID("uuid"), scale);
        }
    }

    public static void write(Level level, CompoundTag data) {
        SubLevelSchematicSerializationContext context = SAVING.get();
        SAVING.remove();
        if (context == null) return;
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        Map<UUID, UUID> live = new HashMap<>();
        context.getMappings().forEach((id, mapping) -> live.put(mapping.newUUID(), id));
        Map<UUID, SchematicListener.Captured> captured = new LinkedHashMap<>();
        ListTag entries = data.getList(SUB_LEVELS, Tag.TAG_COMPOUND);
        for (int i = entries.size() - 1; i >= 0; i--) {
            CompoundTag entry = entries.getCompound(i);
            UUID id = entry.hasUUID("uuid") ? live.get(entry.getUUID("uuid")) : null;
            SubLevel subLevel = id == null ? null : container.getSubLevel(id);
            if (subLevel == null) continue;
            if (Detached.isDetached(subLevel) || Hosted.isHosted(subLevel)) {
                entries.remove(i);
                continue;
            }
            double scale = Pym.scale().settled(subLevel);
            if (!ScaleBounds.same(scale, ScaleBounds.FULL)) entry.putDouble(KEY, scale);
            captured.put(id, new SchematicListener.Captured(entry.getUUID("uuid"), subLevel, entry,
                    context.getMappings().get(id).transform().apply(BlockPos.ZERO)));
        }
        if (entries.isEmpty()) data.remove(SUB_LEVELS);

        BoundingBox3i bounds = context.getBoundingBox();
        SubLevel around = Sable.HELPER.getContaining(level, new Vector3d(
                (bounds.minX() + bounds.maxX() + 1) * 0.5D,
                (bounds.minY() + bounds.maxY() + 1) * 0.5D,
                (bounds.minZ() + bounds.maxZ() + 1) * 0.5D));
        if (around != null && (Detached.isDetached(around) || Hosted.isHosted(around))) around = null;
        if (around != null) {
            double scale = Pym.scale().settled(around);
            if (!ScaleBounds.same(scale, ScaleBounds.FULL)) data.putDouble(KEY, scale);
        }
        if (captured.isEmpty()) return;

        ListTag size = data.getList("size", Tag.TAG_INT);
        SchematicListener.Saved saved = new SchematicListener.Saved(level, captured, around,
                context.getPlaceTransform().apply(BlockPos.ZERO).multiply(-1),
                new Vec3i(size.getInt(0), size.getInt(1), size.getInt(2)));
        for (SchematicListener listener : PymExtensions.schematicListeners()) {
            try {
                listener.saved(saved);
            } catch (RuntimeException exception) {
                LOGGER.error("Schematic save listener failed", exception);
            }
        }
    }

    public static void open(Level level, CompoundTag data) {
        double grid = data.contains(KEY, Tag.TAG_ANY_NUMERIC) ? data.getDouble(KEY) : ScaleBounds.FULL;
        if (!ScaleBounds.isValid(grid)) grid = ScaleBounds.FULL;
        GRID.set(grid);
        if (ScaleBounds.same(grid, ScaleBounds.FULL)) return;
        if (!level.isClientSide() && (INSTANT.get() == null || ratio(grid, INSTANT.get()) == ScaleBounds.FULL)) return;

        CompoundTag body = new CompoundTag();
        for (String key : List.of("blocks", "palette", "palettes", "entities")) {
            if (!data.contains(key)) continue;
            body.put(key, data.get(key));
            data.put(key, new ListTag());
        }
        data.remove("palettes");
        body.put("size", data.get("size").copy());
        body.putInt("DataVersion", data.getInt("DataVersion"));
        body.putUUID("uuid", BODY);
        body.put("position", SableNBTUtils.writeVector3d(new Vector3d()));
        body.put("orientation", SableNBTUtils.writeQuaternion(new Quaterniond()));
        body.putDouble(KEY, grid);
        ListTag entries = data.getList(SUB_LEVELS, Tag.TAG_COMPOUND);
        entries.add(body);
        data.put(SUB_LEVELS, entries);
    }

    public static void forget() {
        GRID.remove();
    }

    public static double grid() {
        Double grid = GRID.get();
        return grid == null ? ScaleBounds.FULL : grid;
    }

    public static boolean merges(Level level, BlockPos at) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, at);
        return Detached.isDetached(subLevel) || Hosted.isHosted(subLevel);
    }

    public static double around(Level level, BlockPos at) {
        return merges(level, at) ? Double.NaN : Pym.scale().at(level, at);
    }

    public static double ratio(double grid, double around) {
        if (ScaleBounds.same(grid, ScaleBounds.FULL) || Double.isNaN(around)) return ScaleBounds.FULL;
        double ratio = grid / around;
        return Math.abs(ratio - ScaleBounds.FULL) <= TOLERANCE ? ScaleBounds.FULL : ratio;
    }

    public static Vec3 fine(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(FINE)) return Vec3.ZERO;
        ListTag fine = data.copyTag().getList(FINE, Tag.TAG_DOUBLE);
        return fine.size() == 3 ? new Vec3(fine.getDouble(0), fine.getDouble(1), fine.getDouble(2)) : Vec3.ZERO;
    }

    public static void fine(ItemStack stack, Vec3 fine) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (fine.equals(Vec3.ZERO)) {
            if (!data.contains(FINE)) return;
            data.remove(FINE);
        } else {
            ListTag list = new ListTag();
            list.add(DoubleTag.valueOf(Mth.clamp(fine.x, 0.0D, 1.0D)));
            list.add(DoubleTag.valueOf(Mth.clamp(fine.y, 0.0D, 1.0D)));
            list.add(DoubleTag.valueOf(Mth.clamp(fine.z, 0.0D, 1.0D)));
            data.put(FINE, list);
        }
        if (data.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    public static void fine(ServerPlayer player, int slot, Vec3 fine) {
        if (slot < -1 || slot >= player.getInventory().getContainerSize()) return;
        ItemStack stack = slot == -1 ? player.getMainHandItem() : player.getInventory().getItem(slot);
        if (AllItems.SCHEMATIC.isIn(stack) && Double.isFinite(fine.x) && Double.isFinite(fine.y) && Double.isFinite(fine.z))
            fine(stack, fine);
    }

    public static void instant(SchematicPrinter printer, ItemStack stack, Level level, Runnable load) {
        BlockPos anchor = stack.getOrDefault(AllDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO);
        double around = around(level, anchor);
        INSTANT.set(around);
        GRID.remove();
        try {
            load.run();
        } finally {
            INSTANT.remove();
        }
        if (!printer.isLoaded() || printer.isErrored()) return;

        double ratio = ratio(grid(), around);
        double factor = Double.isNaN(around) ? Double.NaN : ratio == ScaleBounds.FULL ? around / grid() : ScaleBounds.FULL;
        SchematicLevel schematic = ((SchematicPrinterExtension) printer).sable$getSchematicLevel();
        List<SchematicLevelExtension.SchematicSubLevel> subLevels = ((SchematicLevelExtension) schematic).sable$getSubLevels();
        if (!Double.isNaN(factor) && level instanceof ServerLevel server && !permitted(server, subLevels, factor)) {
            subLevels.clear();
            ((SchematicPrinterErrorAccessor) printer).pym$setErrored(true);
            return;
        }
        PLACING.set(new Placing(level, SchematicItem.getSettings(stack), anchor, factor));

        if (ratio == ScaleBounds.FULL) return;
        Vec3 fine = fine(stack);
        for (SchematicLevelExtension.SchematicSubLevel subLevel : subLevels)
            subLevel.position().mul(ratio).add(fine.x, fine.y, fine.z);
    }

    private static boolean permitted(ServerLevel level, List<SchematicLevelExtension.SchematicSubLevel> subLevels, double factor) {
        for (SchematicLevelExtension.SchematicSubLevel subLevel : subLevels) {
            double target = of(subLevel.uuid()) * factor;
            for (SchematicListener listener : PymExtensions.schematicListeners()) {
                try {
                    if (!listener.permits(level, target)) return false;
                } catch (RuntimeException exception) {
                    LOGGER.error("Schematic scale check failed", exception);
                }
            }
        }
        return true;
    }

    private static void place(SubLevelSchematicSerializationContext context) {
        Placing placing = PLACING.get();
        PLACING.remove();
        if (placing == null || !(placing.level() instanceof ServerLevel level)) return;
        if (!(SubLevelContainer.getContainer(level) instanceof ServerSubLevelContainer container)) return;

        Map<UUID, SchematicListener.Restored> restored = new LinkedHashMap<>();
        context.getMappings().forEach((saved, mapping) -> {
            if (!(container.getSubLevel(mapping.newUUID()) instanceof ServerSubLevel subLevel)) return;
            Double scale = KNOWN.get(saved);
            if (Double.isNaN(placing.factor())) {
                if (scale != null) resize(container, subLevel, scale);
            } else {
                double target = (scale == null ? ScaleBounds.FULL : scale) * placing.factor();
                if (!ScaleBounds.same(Pym.scale().settled(subLevel), target)) resize(container, subLevel, target);
            }
            restored.put(saved, new SchematicListener.Restored(subLevel, mapping.transform().apply(BlockPos.ZERO)));
        });

        SchematicListener.Restored body = restored.remove(BODY);
        SchematicListener.Placed placed = new SchematicListener.Placed(level, restored, body, placing.settings(), placing.anchor());
        for (SchematicListener listener : PymExtensions.schematicListeners()) {
            try {
                listener.placed(placed);
            } catch (RuntimeException exception) {
                LOGGER.error("Schematic place listener failed", exception);
            }
        }
    }

    private record Placing(Level level, StructurePlaceSettings settings, BlockPos anchor, double factor) {}

    private static void resize(ServerSubLevelContainer container, ServerSubLevel subLevel, double scale) {
        BlockPos origin = subLevel.getPlot().getCenterBlock();
        Vector3d placed = subLevel.logicalPose().transformPosition(new Vector3d(origin.getX(), origin.getY(), origin.getZ()));
        if (!Pym.resize().request(subLevel).scaleTo(scale).immediate().submit().accepted()) return;

        Vector3d moved = subLevel.logicalPose().transformPosition(new Vector3d(origin.getX(), origin.getY(), origin.getZ()));
        Vector3d position = new Vector3d(subLevel.logicalPose().position()).add(placed).sub(moved);
        container.physicsSystem().getPipeline().teleport(subLevel, position, subLevel.logicalPose().orientation());
        subLevel.logicalPose().position().set(position);
        subLevel.updateBoundingBox();
        subLevel.updateLastPose();
    }

    private SchematicScales() {}
}
