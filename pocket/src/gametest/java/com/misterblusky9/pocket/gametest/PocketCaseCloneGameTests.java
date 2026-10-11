package com.misterblusky9.pocket.gametest;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(PocketSized.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PocketCaseCloneGameTests {
    @GameTest(template = "empty8")
    public static void creativeCloneOfFilledCaseIsEmpty(final GameTestHelper helper) {
        final ServerPlayer player = creative(helper);
        player.getInventory().setItem(0, filledCase(helper.getLevel()));

        final int slot = player.inventoryMenu.findSlot(player.getInventory(), 0).orElseThrow();
        player.inventoryMenu.clicked(slot, 2, ClickType.CLONE, player);
        final ItemStack carried = player.inventoryMenu.getCarried();

        helper.assertTrue(carried.is(ModItems.BRASS_DISPLAY_CASE.get()), "clone gave " + carried);
        helper.assertTrue(!PocketCaseItem.isFilled(carried), "clone kept the payload");
        helper.assertTrue(PocketCaseItem.isFilled(player.getInventory().getItem(0)), "the original lost its payload");
        helper.succeed();
    }

    @GameTest(template = "empty8")
    public static void creativeDragOfFilledCaseSpreadsEmpties(final GameTestHelper helper) {
        final ServerPlayer player = creative(helper);
        final ItemStack filled = filledCase(helper.getLevel());
        final InventoryMenu menu = player.inventoryMenu;
        final int first = menu.findSlot(player.getInventory(), 0).orElseThrow();
        final int second = menu.findSlot(player.getInventory(), 1).orElseThrow();
        menu.setCarried(filled);

        final int type = AbstractContainerMenu.QUICKCRAFT_TYPE_CLONE;
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, type), ClickType.QUICK_CRAFT, player);
        menu.clicked(first, AbstractContainerMenu.getQuickcraftMask(1, type), ClickType.QUICK_CRAFT, player);
        menu.clicked(second, AbstractContainerMenu.getQuickcraftMask(1, type), ClickType.QUICK_CRAFT, player);
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, type), ClickType.QUICK_CRAFT, player);

        for (final int slot : new int[] {0, 1}) {
            final ItemStack placed = player.getInventory().getItem(slot);
            helper.assertTrue(placed.is(ModItems.BRASS_DISPLAY_CASE.get()), "slot " + slot + " got " + placed);
        }
        helper.assertTrue(PocketCaseItem.isFilled(menu.getCarried()), "the cursor lost the real case: " + menu.getCarried());
        helper.succeed();
    }

    private static ServerPlayer creative(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "pocket-test"), ClientInformation.createDefault());
        player.getAbilities().instabuild = true;
        return player;
    }

    private static ItemStack filledCase(final ServerLevel level) {
        final ItemStack filled = PocketCaseItem.createFilled(
                new ItemStack(ModItems.POCKETED_SUBLEVEL.get()), UUID.randomUUID(), level, "clone-test", null, 1, 0, 1.0D);
        PocketCaseItem.setContainer(filled, new ItemStack(ModItems.BRASS_DISPLAY_CASE.get()));
        return filled;
    }

    private PocketCaseCloneGameTests() {}
}
