package com.misterblusky9.pocket.gametest;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.item.CaseSeal;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.item.PocketSeal;
import com.misterblusky9.pocket.pocket.PocketedSubLevelSavedData;
import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.logistics.box.PackageEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(PocketSized.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PocketCaseSealGameTests {
    @GameTest(template = "empty8", timeoutTicks = 60)
    public static void onlyOwnerCanCutCaseSeal(final GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }

        final ServerLevel level = helper.getLevel();
        final ServerPlayer owner = player(helper);
        final ServerPlayer stranger = player(helper);
        final ItemStack box = PocketCaseItem.createFilled(
                new ItemStack(ModItems.POCKETED_SUBLEVEL.get()), UUID.randomUUID(), level, "seal-test", null, 1, 0, 1.0D);
        PocketCaseItem.setContainer(box, new ItemStack(ModItems.BRASS_DISPLAY_CASE.get()));
        helper.assertTrue(PocketCaseItem.isSealable(box), "case is not sealable");

        final Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 4)));
        final PackageEntity pocketed = new PackageEntity(level, at.x, at.y, at.z);
        pocketed.setBox(box);
        level.addFreshEntity(pocketed);

        helper.runAfterDelay(5, () -> {
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllItems.SUPER_GLUE.get()));
            interact(owner, pocketed);
            helper.assertTrue(PocketCaseItem.seal(pocketed.getBox()) == PocketSeal.SUPER_GLUE,
                    "the case did not seal");

            stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.POCKET_KNIFE.get()));
            interact(stranger, pocketed);
            helper.assertTrue(PocketCaseItem.seal(pocketed.getBox()) == PocketSeal.SUPER_GLUE,
                    "another player cut the seal");

            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.POCKET_KNIFE.get()));
            interact(owner, pocketed);
            helper.assertTrue(PocketCaseItem.seal(pocketed.getBox()) == null,
                    "the owner could not cut the seal");
            pocketed.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty8", timeoutTicks = 60)
    public static void killedSealedCaseIsGone(final GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }

        final ServerLevel level = helper.getLevel();
        final ServerPlayer owner = player(helper);
        final UUID token = UUID.randomUUID();
        final PocketedSubLevelSavedData storage = PocketedSubLevelSavedData.getOrLoad(level);
        storage.put(token, new CompoundTag());
        final ItemStack box = PocketCaseItem.createFilled(
                new ItemStack(ModItems.POCKETED_SUBLEVEL.get()), token, level, "kill-test", null, 1, 0, 1.0D);
        PocketCaseItem.setContainer(box, new ItemStack(ModItems.BRASS_DISPLAY_CASE.get()));

        final Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 4)));
        final PackageEntity pocketed = new PackageEntity(level, at.x, at.y, at.z);
        pocketed.setBox(box);
        level.addFreshEntity(pocketed);

        helper.runAfterDelay(5, () -> {
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllItems.SUPER_GLUE.get()));
            interact(owner, pocketed);
            helper.assertTrue(PocketCaseItem.isSealed(pocketed.getBox()), "the case did not seal");

            pocketed.kill();
            helper.assertTrue(pocketed.isRemoved(), "the sealed case survived /kill");

            helper.runAfterDelay(3, () -> {
                final List<? extends PackageEntity> respawned = level.getEntities(
                        EntityTypeTest.forClass(PackageEntity.class),
                        dropped -> token.equals(PocketCaseItem.token(dropped.getBox())));
                helper.assertTrue(respawned.isEmpty(), "/kill dropped " + respawned.size() + " replacement cases");

                storage.remove(token);
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty8", timeoutTicks = 60)
    public static void caseInTheVoidIsGone(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final UUID token = UUID.randomUUID();
        final PocketedSubLevelSavedData storage = PocketedSubLevelSavedData.getOrLoad(level);
        storage.put(token, new CompoundTag());
        final ItemStack box = PocketCaseItem.createFilled(
                new ItemStack(ModItems.POCKETED_SUBLEVEL.get()), token, level, "void-test", null, 1, 0, 1.0D);
        PocketCaseItem.setContainer(box, new ItemStack(ModItems.BRASS_DISPLAY_CASE.get()));
        PocketCaseItem.setSeal(box, new CaseSeal(PocketSeal.SUPER_GLUE, UUID.randomUUID(), "void-test"));

        final Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 4)));
        final PackageEntity pocketed = new PackageEntity(level, at.x, level.getMinBuildHeight() - 70.0D, at.z);
        pocketed.setBox(box);
        level.addFreshEntity(pocketed);

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(pocketed.isRemoved(), "the sealed case survived the void");
            final List<? extends PackageEntity> respawned = level.getEntities(
                    EntityTypeTest.forClass(PackageEntity.class),
                    dropped -> token.equals(PocketCaseItem.token(dropped.getBox())));
            helper.assertTrue(respawned.isEmpty(), "the case respawned " + respawned.size() + " times");

            storage.remove(token);
            helper.succeed();
        });
    }

    private static void interact(final ServerPlayer player, final PackageEntity pocketed) {
        if (!player.canInteractWithEntity(pocketed.getBoundingBox(), 1.0D)) {
            throw new AssertionError("the test case was out of reach");
        }
        final InteractionResult at = CommonHooks.onInteractEntityAt(
                player, pocketed, pocketed.position(), InteractionHand.MAIN_HAND);
        if (at == null || !at.consumesAction()) player.interactOn(pocketed, InteractionHand.MAIN_HAND);
    }

    private static ServerPlayer player(final GameTestHelper helper) {
        final ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "pocket-test"), ClientInformation.createDefault());
        final Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 4)));
        player.moveTo(at.x, at.y, at.z, -90.0F, 0.0F);
        return player;
    }

    private PocketCaseSealGameTests() {}
}
