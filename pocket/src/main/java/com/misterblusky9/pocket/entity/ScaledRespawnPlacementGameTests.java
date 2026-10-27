package com.misterblusky9.pocket.entity;

import com.misterblusky9.pocket.PocketSized;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Local placement search only: no Sable sublevel, cloning or client sync.
@GameTestHolder(PocketSized.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScaledRespawnPlacementGameTests {
    private static final BlockPos FOOT = new BlockPos(2, 2, 2);

    @GameTest(template = "recycler_empty")
    public static void floatingBedOnlyAtSixteenth(GameTestHelper test) {
        bedOnly(test, 1.0D / 16.0D, 1.0D / 16.0D);
    }

    @GameTest(template = "recycler_empty")
    public static void floatingBedOnlyAtEightfold(GameTestHelper test) {
        bedOnly(test, 8.0D, 8.0D);
    }

    @GameTest(template = "recycler_empty")
    public static void normalPlayerOnEightfoldBed(GameTestHelper test) {
        bedOnly(test, 1.0D, 8.0D);
    }

    @GameTest(template = "recycler_empty")
    public static void bedWithNearbyFloor(GameTestHelper test) {
        BlockPos foot = placeBed(test);
        ServerLevel level = test.getLevel();
        level.setBlock(foot.east().below(), Blocks.STONE.defaultBlockState(), 2);
        Vec3 standing = ScaledRespawn.findStandingRoom(level, foot, 8.0D, 8.0D, true);
        test.assertTrue(standing != null, "Adjacent floor should offer a landing");
        test.assertTrue(Math.abs(standing.y - foot.getY()) < 0.01D,
                "Expected floor-level landing rather than bed top");
        test.succeed();
    }

    @GameTest(template = "recycler_empty")
    public static void blockedBedOnlyHasNoLanding(GameTestHelper test) {
        BlockPos foot = placeBed(test);
        ServerLevel level = test.getLevel();
        level.setBlock(foot.above(), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(foot.north().above(), Blocks.STONE.defaultBlockState(), 2);
        Vec3 standing = ScaledRespawn.findStandingRoom(level, foot, 8.0D, 8.0D, true);
        test.assertTrue(standing == null, "Bed must not place a player into an obstructed hitbox");
        test.succeed();
    }

    @GameTest(template = "recycler_empty")
    public static void missingBedHalfIsInvalid(GameTestHelper test) {
        BlockPos foot = placeBed(test);
        ServerLevel level = test.getLevel();
        // KNOWN_SHAPE: otherwise BedBlock.updateShape removes the foot as well
        level.setBlock(foot.north(), Blocks.AIR.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        test.assertTrue(level.getBlockState(foot).getBlock() instanceof BedBlock, "Foot half must survive");
        test.assertTrue(!ScaledRespawn.validBed(level, foot, level.getBlockState(foot)),
                "Orphaned bed half must fail validation");
        test.succeed();
    }

    @GameTest(template = "recycler_empty")
    public static void isolatedAnchorHasLanding(GameTestHelper test) {
        BlockPos pos = test.absolutePos(FOOT);
        ServerLevel level = test.getLevel();
        clearSupport(level, pos);
        level.setBlock(pos, Blocks.RESPAWN_ANCHOR.defaultBlockState()
                .setValue(RespawnAnchorBlock.CHARGE, 4), 2);
        Vec3 standing = ScaledRespawn.findStandingRoom(level, pos, 1.0D / 16.0D, 1.0D / 16.0D, false);
        test.assertTrue(standing != null, "Bed-sized anchor-only craft must offer a landing");
        test.assertTrue(level.noCollision(standingBox(standing, 1.0D)),
                "Landing above anchor should be collision-free in local space");
        test.succeed();
    }

    private static void bedOnly(GameTestHelper test, double playerScale, double bedScale) {
        BlockPos foot = placeBed(test);
        ServerLevel level = test.getLevel();
        test.assertTrue(ScaledRespawn.validBed(level, foot, level.getBlockState(foot)),
                "Placed two-block bed must be valid");
        Vec3 standing = ScaledRespawn.findStandingRoom(level, foot, playerScale, bedScale, true);
        test.assertTrue(standing != null, "No landing for player=" + playerScale + " bed=" + bedScale);
        test.assertTrue(level.noCollision(standingBox(standing, playerScale / bedScale)),
                "Landing overlaps local blocks for player=" + playerScale + " bed=" + bedScale);
        test.succeed();
    }

    private static AABB standingBox(Vec3 standing, double relative) {
        double half = 0.3D * relative;
        return new AABB(standing.x - half, standing.y + 1.0E-5D, standing.z - half,
                standing.x + half, standing.y + 1.8D * relative, standing.z + half);
    }

    private static BlockPos placeBed(GameTestHelper test) {
        BlockPos foot = test.absolutePos(FOOT);
        ServerLevel level = test.getLevel();
        clearSupport(level, foot);
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 2);
        level.setBlock(foot.north(), bed.setValue(BedBlock.PART, BedPart.HEAD), 2);
        return foot;
    }

    // test floor sits under the template; bed-only cases need it gone
    private static void clearSupport(ServerLevel level, BlockPos centre) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlock(centre.offset(dx, -1, dz), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(centre.offset(dx, -2, dz), Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private ScaledRespawnPlacementGameTests() {}
}
