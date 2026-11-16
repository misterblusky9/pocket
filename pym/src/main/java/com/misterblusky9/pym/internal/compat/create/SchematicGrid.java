package com.misterblusky9.pym.internal.compat.create;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.network.SchematicFinePayload;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.schematics.SchematicItem;
import com.simibubi.create.content.schematics.client.SchematicTransformation;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.schematics.StructureTemplateExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3d;

import java.util.UUID;

public final class SchematicGrid {
    private static double grid = ScaleBounds.FULL;
    private static Vec3 fine = Vec3.ZERO;
    private static Vec3 aimed = null;
    private static AABB extent = null;

    public interface Placement {
        Vec3 pym$anchorFine();
        void pym$placeAt(Vec3 corner, double ratio);
        void pym$placeFull();
    }

    public static void show(ItemStack blueprint) {
        SchematicScales.forget();
        StructureTemplate template = SchematicItem.loadSchematic(Minecraft.getInstance().level, blueprint);
        grid = SchematicScales.grid();
        fine = SchematicScales.fine(blueprint);
        extent = extent(template);
    }

    public static AABB outline(AABB bounds) {
        return extent == null ? bounds : bounds.minmax(extent);
    }

    private static AABB extent(StructureTemplate template) {
        AABB box = null;
        for (StructureTemplateExtension.SubLevelTemplate subLevel : ((StructureTemplateExtension) template).sable$getSubLevels()) {
            Vec3i size = subLevel.template().getSize();
            if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) continue;
            double scale = subLevel.uuid().equals(SchematicScales.BODY) ? ScaleBounds.FULL : SchematicScales.of(subLevel.uuid()) / grid;
            for (int corner = 0; corner < 8; corner++) {
                Vector3d point = new Vector3d(
                        (corner & 1) == 0 ? 0 : size.getX(),
                        (corner & 2) == 0 ? 0 : size.getY(),
                        (corner & 4) == 0 ? 0 : size.getZ()).mul(scale);
                subLevel.orientation().transform(point).add(subLevel.position());
                AABB at = new AABB(point.x, point.y, point.z, point.x, point.y, point.z);
                box = box == null ? at : box.minmax(at);
            }
        }
        return box;
    }

    public static Vec3 fine() {
        return fine;
    }

    public static void aim(Vec3 point) {
        aimed = point;
    }

    public static Vec3 snapped(BlockPos selected) {
        Vec3 block = Vec3.atLowerCornerOf(selected);
        double ratio = ratio(selected);
        if (ratio == ScaleBounds.FULL) return block;
        Vec3 point = aimed;
        if (point == null || !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                || Math.abs(point.x - block.x) > 1.000001D
                || Math.abs(point.y - block.y) > 1.000001D
                || Math.abs(point.z - block.z) > 1.000001D) point = block;
        return new Vec3(snap(point.x, ratio), snap(point.y, ratio), snap(point.z, ratio));
    }

    private static double snap(double value, double step) {
        double cells = value / step;
        double nearest = Math.rint(cells);
        if (Math.abs(cells - nearest) < 1.0E-7D) cells = nearest;
        return Math.floor(cells) * step;
    }

    public static double ratio(BlockPos at) {
        if (at == null || ScaleBounds.same(grid, ScaleBounds.FULL)) return ScaleBounds.FULL;
        return SchematicScales.ratio(grid, SchematicScales.around(Minecraft.getInstance().level, at));
    }

    public static double relative(UUID subLevel) {
        return subLevel.equals(SchematicScales.BODY) ? ScaleBounds.FULL : SchematicScales.of(subLevel) / grid;
    }


    public static void sync(ItemStack blueprint, int slot, SchematicTransformation transformation) {
        Vec3 placed = ((Placement) transformation).pym$anchorFine();
        if (placed.equals(SchematicScales.fine(blueprint))) return;
        SchematicScales.fine(blueprint, placed);
        PacketDistributor.sendToServer(new SchematicFinePayload(slot, placed.x, placed.y, placed.z));
    }

    public static void print(ItemStack blueprint, SchematicTransformation transformation) {
        Vec3 placed = ((Placement) transformation).pym$anchorFine();
        if (placed.equals(Vec3.ZERO) && SchematicScales.fine(blueprint).equals(Vec3.ZERO)) return;
        blueprint.set(AllDataComponents.SCHEMATIC_ANCHOR, transformation.getAnchor());
        SchematicScales.fine(blueprint, placed);
    }

    private SchematicGrid() {}
}
