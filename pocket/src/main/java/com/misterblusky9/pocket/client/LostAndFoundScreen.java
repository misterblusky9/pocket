package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.HatStandBlockEntity;
import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.item.LostAndFoundMenu;
import com.misterblusky9.pocket.item.ModDataComponents;
import com.misterblusky9.pocket.network.LostAndFoundSearchPayload;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.WeakHashMap;

public final class LostAndFoundScreen extends AbstractContainerScreen<LostAndFoundMenu> {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "textures/gui/lost_and_found.png");
    private static final ResourceLocation SCROLLER =
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "lost_and_found/scroller");
    private static final ResourceLocation SCROLLER_DISABLED =
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "lost_and_found/scroller_disabled");
    private static final int SCROLL_X = 175;
    private static final int SCROLL_Y = 18;
    private static final int SCROLL_HEIGHT = 90;
    private static final Map<ClientSubLevel, Boolean> SHOWS = new WeakHashMap<>();

    private EditBox searchBox;
    private int row;
    private boolean scrolling;

    public LostAndFoundScreen(LostAndFoundMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 195;
        imageHeight = 194;
    }

    @Override
    protected void init() {
        super.init();
        String previous = searchBox == null ? "" : searchBox.getValue();
        searchBox = new EditBox(font, leftPos + 82, topPos + 6, 80, font.lineHeight,
                Component.translatable("itemGroup.search"));
        searchBox.setMaxLength(50);
        searchBox.setValue(previous);
        searchBox.setBordered(false);
        searchBox.setVisible(true);
        searchBox.setTextColor(0xFFFFFF);
        searchBox.setCanLoseFocus(false);
        searchBox.setFocused(true);
        searchBox.setResponder(text -> {
            row = 0;
            PacketDistributor.sendToServer(new LostAndFoundSearchPayload(text));
        });
        addWidget(searchBox);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        searchBox.render(graphics, mouseX, mouseY, partialTick);
        int top = topPos + SCROLL_Y;
        int rows = menu.rows();
        int knob = rows == 0 ? 0 : (int) ((SCROLL_HEIGHT - 17) * (float) Math.min(row, rows) / rows);
        graphics.blitSprite(rows == 0 ? SCROLLER_DISABLED : SCROLLER, leftPos + SCROLL_X, top + knob, 12, 15);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, 0x404040, false);
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (!(slot.container instanceof Inventory) && invisible(slot.getItem())) {
            TextureAtlasSprite head = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(InventoryMenu.EMPTY_ARMOR_SLOT_HELMET);
            graphics.blit(slot.x, slot.y, 0, 16, 16, head);
        }
        super.renderSlot(graphics, slot);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(key, scanCode, modifiers);
        if (searchBox.keyPressed(key, scanCode, modifiers)) return true;
        return searchBox.isFocused() || super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return searchBox.charTyped(character, modifiers) || super.charTyped(character, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && searchBox.isMouseOver(mouseX, mouseY)) {
            searchBox.setValue("");
            return true;
        }
        if (button == 0 && onScroller(mouseX, mouseY) && menu.rows() > 0) {
            scrolling = true;
            drag(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrolling) {
            drag(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) scrolling = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menu.rows() == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        scrollTo(row - (int) Math.signum(scrollY));
        return true;
    }

    private boolean onScroller(double mouseX, double mouseY) {
        double x = mouseX - leftPos - SCROLL_X;
        double y = mouseY - topPos - SCROLL_Y;
        return x >= 0 && x < 14 && y >= 0 && y < SCROLL_HEIGHT;
    }

    private void drag(double mouseY) {
        float fraction = ((float) (mouseY - topPos - SCROLL_Y) - 7.5F) / (SCROLL_HEIGHT - 15.0F);
        scrollTo(Math.round(Mth.clamp(fraction, 0.0F, 1.0F) * menu.rows()));
    }

    private void scrollTo(int target) {
        int clamped = Mth.clamp(target, 0, menu.rows());
        if (clamped == row) return;
        row = clamped;
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, row);
    }

    private static boolean invisible(ItemStack stack) {
        if (BuildHostBlockEntity.token(stack) == null || !stack.getOrDefault(ModDataComponents.CUSTOM_SPRITE.get(), true)
                || HatStandBlockEntity.headwearLayer(stack) != HatStandBlockEntity.LAYER_REMOVED) return false;
        ClientSubLevel mirror = BuildMirrors.get(stack);
        if (mirror == null) return true;
        if (shows(mirror)) return false;
        for (BuildMirrors.Rider rider : BuildMirrors.riders(BuildHostBlockEntity.token(stack)))
            if (shows(rider.mirror())) return false;
        return true;
    }

    private static boolean shows(ClientSubLevel mirror) {
        return SHOWS.computeIfAbsent(mirror, LostAndFoundScreen::scan);
    }

    private static boolean scan(ClientSubLevel mirror) {
        for (PlotChunkHolder holder : mirror.getPlot().getLoadedChunks()) {
            for (LevelChunkSection section : holder.getChunk().getSections()) {
                if (section.hasOnlyAir()) continue;
                for (int y = 0; y < 16; y++)
                    for (int x = 0; x < 16; x++)
                        for (int z = 0; z < 16; z++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (!state.isAir() && !state.is(ModBlocks.POCKET_DUMMY.get())) return true;
                        }
            }
        }
        return false;
    }
}
