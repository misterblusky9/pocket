package com.misterblusky9.pocket.ponder;

import com.misterblusky9.pym.api.client.PymPonder;
import com.misterblusky9.pym.api.client.PymPonderPalette;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlock;
import dev.simulated_team.simulated.index.SimBlocks;
import dev.simulated_team.simulated.ponder.instructions.PullTheAssemblerKronkInstruction;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PocketPonderScenes {
    private PocketPonderScenes() {}

    public static void scaling(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("scaling_simulated_contraptions", "Scaling Simulated Contraptions");
        scene.configureBasePlate(0, 0, 5);

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        BlockPos tablePos = util.grid().at(2, 1, 2);
        BlockPos assemblerPos = util.grid().at(2, 1, 1);

        var table = scene.world().showIndependentSection(util.select().position(tablePos), Direction.UP);

        Vec3 pivot = new Vec3(2, 1, 2);
        AABB normal = new AABB(2, 1, 2, 3, 2, 3);
        AABB halved = new AABB(2, 1, 2, 2.5, 1.5, 2.5);
        AABB doubled = new AABB(2, 1, 2, 4, 3, 4);

        scene.idle(20);

        scene.world().setBlock(assemblerPos, SimBlocks.PHYSICS_ASSEMBLER.getDefaultState().setValue(PhysicsAssemblerBlock.FACE, AttachFace.WALL).setValue(PhysicsAssemblerBlock.FACING, Direction.NORTH), false);

        var assembler = scene.world().showIndependentSection(util.select().position(assemblerPos), Direction.NORTH);

        scene.world().moveSection(assembler, new Vec3(0, 0, -2), 0);
        scene.idle(5);

        scene.world().moveSection(assembler, new Vec3(0, 0, 2), 5);
        scene.idle(5);

        scene.addInstruction(new PullTheAssemblerKronkInstruction(assemblerPos,true, false));

        scene.idle(5);

        scene.overlay().showText(5).text("Simulated Contraptions can change size!").pointAt(util.vector().centerOf(tablePos)).placeNearTarget();

        scene.idle(5);

        scene.world().hideIndependentSection(assembler, Direction.NORTH);
        scene.idle(5);

        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, "scale", normal, 100);
        scene.idle(20);

        PymPonderPalette.CYAN.chaseBoundingBoxOutline(scene, "scale", halved, 100);
        scene.idle(20);

        PymPonder.scaleSection(scene, table, pivot, 0.5, 20);
        scene.idle(40);

        PymPonderPalette.YELLOW.chaseBoundingBoxOutline(scene, "scale", doubled, 100);
        scene.idle(20);

        PymPonder.scaleSection(scene, table, pivot, 2, 20);
        scene.idle(40);
    }
}