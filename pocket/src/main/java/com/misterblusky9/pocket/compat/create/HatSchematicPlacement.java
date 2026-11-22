package com.misterblusky9.pocket.compat.create;

import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pocket.build.HatSeals;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.schematics.SchematicItem;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.schematics.StructureTemplateExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;


public final class HatSchematicPlacement {
    public static boolean holds(ItemStack stack) {
        return AllItems.SCHEMATIC.isIn(stack) && stack.has(AllDataComponents.SCHEMATIC_FILE);
    }

    public static boolean place(ServerLevel level, ServerPlayer player, BuildHostBlockEntity stand, BlockPos anchor,
                                Rotation rotation, Mirror mirror) {
        ItemStack stack = player.getMainHandItem();
        ServerSubLevel hat = stand.getHat() == null ? null : BuildSites.live(stand.getHat());
        if (!player.isCreative() || !holds(stack) || hat == null || hat.getLevel() != level || !player.mayBuild()
                || HatSeals.locks(stand, player)) return false;
        StructureTemplate template = SchematicItem.loadSchematic(level, stack);
        if (template.getSize().getX() <= 0 || template.getSize().getY() <= 0 || template.getSize().getZ() <= 0) return false;
        if (!((StructureTemplateExtension) template).sable$getSubLevels().isEmpty()) {
            PocketCaseItem.fail(player, "pocket.case.hat_too_complex.schematic");
            return false;
        }
        PocketCaseItem.HatFit fit = PocketCaseItem.hatFit(level, hat, template.getSize(),
                PocketCaseItem.templateSolids(template), anchor, rotation, mirror);
        if (!fit.inside()) {
            PocketCaseItem.fail(player, "pocket.case.hat_blocked");
            return false;
        }
        template = PocketCaseItem.withoutOverlaps(level, template, fit.overlaps());
        StructurePlaceSettings settings = SchematicItem.getSettings(stack, !player.canUseGameMasterBlocks())
                .setRotation(rotation)
                .setMirror(mirror)
                .addProcessor(BlockIgnoreProcessor.AIR);
        template.placeInWorld(level, anchor, anchor, settings, level.random, 2);
        return true;
    }

    private HatSchematicPlacement() {}
}
