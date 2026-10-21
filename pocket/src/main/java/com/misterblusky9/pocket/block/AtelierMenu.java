package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.item.LiveCaseItem;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class AtelierMenu extends AbstractContainerMenu {
    public static final int CASE_X = 8;
    public static final int CASE_Y = 18;
    public static final int INVENTORY_X = 36;
    public static final int INVENTORY_Y = 144;
    public static final int HOTBAR_Y = 202;

    public static final String[] FIELDS = {"X", "Y", "Z", "Pitch", "Yaw", "Roll"};
    public static final float[] STEPS = {-8.0F, -1.0F, 1.0F, 8.0F};
    public static final float ROTATION_STEP = 5.0F;
    public static final int RESET = FIELDS.length * STEPS.length;

    private final Container input = new SimpleContainer(1);
    private final ContainerLevelAccess access;

    public AtelierMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public AtelierMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.ATELIER.get(), id);
        this.access = access;

        addSlot(new Slot(this.input, 0, CASE_X, CASE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof LiveCaseItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, HOTBAR_Y));
        }
    }

    public ItemStack worked() {
        return getSlot(0).getItem();
    }

    public static float step(int field, int step) {
        return STEPS[step] * (field < 3 ? 1.0F : ROTATION_STEP);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        ItemStack stack = worked();
        if (!(stack.getItem() instanceof LiveCaseItem) || id < 0 || id > RESET) return false;
        float[] pose = LiveCaseItem.wornPose(stack);
        if (id == RESET) {
            pose = new float[LiveCaseItem.WORN_POSE_FIELDS];
        } else {
            int field = id / STEPS.length;
            pose[field] += step(field, id % STEPS.length);
            if (field >= 3) pose[field] = ((pose[field] % 360.0F) + 360.0F) % 360.0F;
        }
        LiveCaseItem.setWornPose(stack, pose);
        getSlot(0).setChanged();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.ATELIER.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, pos) -> clearContainer(player, this.input));
    }
}
