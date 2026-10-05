package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.CreativeShrinkRayItem;
import com.misterblusky9.pocket.item.ScaleSelectingItem;
import com.misterblusky9.pocket.item.SelfResizeDeviceItem;
import com.misterblusky9.pocket.compression.PersonalLock;
import com.misterblusky9.pocket.network.PersonalLockPayload;
import com.misterblusky9.pocket.network.ShrinkRayScalePayload;
import com.misterblusky9.pocket.network.ShrinkRaySettingsPayload;
import com.misterblusky9.pym.api.ScaleBounds;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public final class ScaleSelectionScreen extends Screen {
    private static final double BOARD_MIN = 1.0D / 16.0D;
    private static final double BOARD_MAX = 4.0D;
    private static final double[] EXTENDED_STOPS = {1.0D / 32.0D, 8.0D, 16.0D};
    private static final double[] FINE_STOPS = {
            1.0D / 10.0D, 1.0D / 5.0D, 1.0D / 3.0D, 2.0D / 3.0D, 3.0D / 4.0D,
            1.25D, 1.5D, 1.75D, 2.25D, 2.5D, 2.75D, 3.0D, 3.25D, 3.5D, 3.75D};

    private static final int BODY_WIDTH = 224;
    private static final int SCALE_LABEL_LEFT = 87;
    private static final int SCALE_LABEL_TOP = 20;
    private static final int FIELD_LEFT = 91;
    private static final int FIELD_TOP = 52;
    private static final int FIELD_WIDTH = 38;
    private static final int BAR_LEFT = 91;
    private static final int BAR_TOP = 33;
    private static final int BAR_WIDTH = 125;
    private static final int BAR_HEIGHT = 8;
    private static final int CENTRE = BAR_WIDTH / 2;
    private static final int BELOW_SPACING = 14;
    private static final int ABOVE_SPACING = 20;
    private static final int TARGETING_LABEL_LEFT = 89;
    private static final int TARGETING_LABEL_TOP = 76;
    private static final int TARGET_BUTTON_LEFT = 89;
    private static final int TARGET_BUTTON_TOP = 87;
    private static final int RESET_LEFT = 173;
    private static final int CONFIRM_LEFT = 201;

    private static final int PREVIEW_LEFT = 8;
    private static final int PREVIEW_TOP = 20;
    private static final int PREVIEW_WIDTH = 67;
    private static final int PREVIEW_HEIGHT = 83;
    private static final int PREVIEW_BACKGROUND = 0xFF8B8B8B;

    private static final double PREVIEW_CANONICAL_PIXELS_PER_BLOCK = 32.0D;
    private static final double PREVIEW_FIXED_CAMERA_MIN_SCALE = 1.0D / 4.0D;
    private static final double PREVIEW_FIXED_CAMERA_MAX_SCALE = 4.0D;

    private static final double PREVIEW_MIN_SCALE = 1.0D / 16.0D;
    private static final double PREVIEW_TINY_CAMERA_AT_MIN = 5.0D;

    private static final int PREVIEW_GROUND_INSET = 8;
    private static final float NOT_TO_SCALE_SIZE = 0.75F;
    private static final double PREVIEW_EASE_SECONDS = 0.12D;
    private static final double PREVIEW_TARGET_TOP_MARGIN = 4.0D;
    private static final double PREVIEW_PLAYER_RIGHT_MARGIN = 1.0D;

    private static final double PREVIEW_TINY_PLAYER_FOOT_NUDGE_TEXELS = 4.0D;

    private static final double PREVIEW_PLAYER_HEIGHT_BLOCKS = 2.0D;

    private static final double PREVIEW_CENTRE_GAP = 1.0D;

    private static final Supplier<Block> PREVIEW_GROUND_BLOCK = () -> Blocks.GRASS_BLOCK;

    private static final int TITLE_TEXT = 0x54214F;
    private static final int LABEL_TEXT = 0x787878;
    private static final int TEXT = 0xDDDDDD;
    private static final int CURSOR_TEXT = 0x54214F;
    private static final int PEHKUI_TEXT = 0x214854;

    private PocketGuiTexture background = PocketGuiTexture.SHRINKRAY;
    private final InteractionHand hand;
    private final List<IconButton> targetingButtons = new ArrayList<>(3);

    private ScaleBounds range = ScaleBounds.SAFE;
    private double[] stops = new double[0];
    private double[] detents = new double[0];
    private double[] fieldDetents = new double[0];
    private double[] extendedDetents = new double[0];
    private double initial = ScaleBounds.FULL;
    private double value = ScaleBounds.FULL;
    private double previewValue = Double.NaN;
    private double previewFrom = Double.NaN;
    private double previewTo = Double.NaN;
    private long previewStart;
    private CreativeShrinkRayItem.TargetingMode targeting = CreativeShrinkRayItem.TargetingMode.BOTH;

    private EditBox field;
    private boolean fieldWasFocused;
    private boolean syncing;
    private boolean dragging;
    private int soundCooldown;
    private int left;
    private int top;
    private boolean shrinkRayMenu;
    private boolean playerPreview;
    private boolean unlocked;

    public ScaleSelectionScreen(final InteractionHand hand) {
        super(Component.translatable("pocket.screen.scale_selection"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        final LocalPlayer player = Minecraft.getInstance().player;
        final ItemStack stack = held();
        if (player == null || stack == null) {
            onClose();
            return;
        }
        final ScaleSelectingItem tool = (ScaleSelectingItem) stack.getItem();

        this.range = tool.selectionRange(player);
        this.stops = DoubleStream.concat(
                        IntStream.rangeClosed(-10, -1).mapToDouble(exponent -> Math.pow(2.0D, exponent)),
                        IntStream.rangeClosed(1, (int) BOARD_MAX).asDoubleStream())
                .filter(stop -> stop >= BOARD_MIN - ScaleBounds.EPSILON && stop <= BOARD_MAX + ScaleBounds.EPSILON)
                .filter(this.range::contains)
                .toArray();
        final double[] seen = this.stops;
        this.detents = DoubleStream.concat(DoubleStream.of(seen), DoubleStream.of(FINE_STOPS)
                        .filter(stop -> stop >= BOARD_MIN - ScaleBounds.EPSILON && stop <= BOARD_MAX + ScaleBounds.EPSILON)
                        .filter(this.range::contains)
                        .filter(stop -> DoubleStream.of(seen).noneMatch(other -> ScaleBounds.same(stop, other))))
                .sorted()
                .toArray();
        this.extendedDetents = DoubleStream.concat(DoubleStream.of(this.detents), DoubleStream.of(EXTENDED_STOPS)
                        .filter(this.range::contains))
                .sorted()
                .toArray();
        this.detents = this.extendedDetents;
        this.fieldDetents = this.detents;

        this.initial = tool.selection(stack, player);
        this.value = this.range.clamp(this.initial);
        this.previewValue = this.value;
        this.shrinkRayMenu = stack.getItem() instanceof CreativeShrinkRayItem;
        this.playerPreview = stack.getItem() instanceof SelfResizeDeviceItem;
        this.background = this.playerPreview ? PocketGuiTexture.PERSONAL_COMPRESSOR : PocketGuiTexture.SHRINKRAY;
        if (this.shrinkRayMenu) {
            this.targeting = CreativeShrinkRayItem.targetingMode(stack);
        }
        if (this.playerPreview) {
            this.unlocked = PersonalLock.unlocked(player);
        }

        this.left = (this.width - this.background.getWidth()) / 2;
        this.top = (this.height - this.background.getHeight()) / 2;

        this.field = new EditBox(this.font, this.left + FIELD_LEFT, this.top + FIELD_TOP,
                FIELD_WIDTH, 14, this.title);
        this.field.setBordered(false);
        this.field.setMaxLength(12);
        this.field.setFilter(text -> text.chars().allMatch(c -> "0123456789.,/x×% ¹²³₀₁₂₃₄₆₈".indexOf(c) >= 0));
        this.field.setResponder(this::typed);
        addRenderableWidget(this.field);
        syncField();

        final IconButton confirm = new IconButton(this.left + CONFIRM_LEFT, this.top + TARGET_BUTTON_TOP, AllIcons.I_CONFIRM);
        confirm.withCallback(this::onClose);
        addRenderableWidget(confirm);

        final IconButton reset = new IconButton(this.left + RESET_LEFT, this.top + TARGET_BUTTON_TOP, AllIcons.I_ROTATE_CCW);
        reset.withCallback(this::reset);
        reset.setToolTip(Component.translatable("pocket.screen.shrinkray.reset"));
        addRenderableWidget(reset);

        if (this.shrinkRayMenu) {
            addTargetingButton(0, CreativeShrinkRayItem.TargetingMode.BOTH, PocketIcon.TARGET_BOTH,
                    "pocket.screen.shrinkray.targeting.both");
            addTargetingButton(1, CreativeShrinkRayItem.TargetingMode.CONTRAPTIONS, PocketIcon.TARGET_CONTRAPTIONS,
                    "pocket.screen.shrinkray.targeting.contraptions");
            addTargetingButton(2, CreativeShrinkRayItem.TargetingMode.ENTITIES, PocketIcon.TARGET_ENTITIES,
                    "pocket.screen.shrinkray.targeting.entities");
            refreshTargetingButtons();
        }
        if (this.playerPreview) {
            addLockButton(0, false, AllIcons.I_CONFIG_LOCKED, "pocket.screen.personal.locked");
            addLockButton(1, true, AllIcons.I_CONFIG_UNLOCKED, "pocket.screen.personal.unlocked");
            refreshTargetingButtons();
        }
    }

    private void addLockButton(final int index, final boolean unlocked, final AllIcons icon, final String tooltip) {
        final IconButton button = new IconButton(
                this.left + TARGET_BUTTON_LEFT + index * 18,
                this.top + TARGET_BUTTON_TOP,
                icon);
        button.withCallback(() -> {
            this.unlocked = unlocked;
            refreshTargetingButtons();
        });
        button.setToolTip(Component.translatable(tooltip));
        this.targetingButtons.add(button);
        addRenderableWidget(button);
    }

    private double stepField(final int direction) {
        return step(this.fieldDetents, direction);
    }

    private void addTargetingButton(
            final int index,
            final CreativeShrinkRayItem.TargetingMode mode,
            final PocketIcon icon,
            final String tooltip
    ) {
        final IconButton button = new IconButton(
                this.left + TARGET_BUTTON_LEFT + index * 18,
                this.top + TARGET_BUTTON_TOP,
                icon);
        button.withCallback(() -> setTargeting(mode));
        button.setToolTip(Component.translatable(tooltip));
        this.targetingButtons.add(button);
        addRenderableWidget(button);
    }

    private void setTargeting(final CreativeShrinkRayItem.TargetingMode mode) {
        this.targeting = mode;
        refreshTargetingButtons();
    }

    private void refreshTargetingButtons() {
        for (int i = 0; i < this.targetingButtons.size(); i++) {
            this.targetingButtons.get(i).green = this.playerPreview
                    ? (i == 1) == this.unlocked
                    : this.targeting.ordinal() == i;
        }
    }

    private void reset() {
        releaseField(false);
        if (this.range.contains(ScaleBounds.FULL)) set(ScaleBounds.FULL);
        if (this.shrinkRayMenu) setTargeting(CreativeShrinkRayItem.TargetingMode.BOTH);
        if (this.playerPreview) {
            this.unlocked = false;
            refreshTargetingButtons();
        }
    }

    @Override
    public void tick() {
        if (this.soundCooldown > 0) this.soundCooldown--;
    }

    @Override
    public void renderBackground(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTicks) {
        super.renderBackground(graphics, mouseX, mouseY, partialTicks);
        renderScalePreview(graphics, mouseX, mouseY);
        this.background.render(graphics, this.left, this.top);

        final ItemStack stack = held();
        if (stack == null) return;
        final Component name = stack.getHoverName();
        graphics.drawString(this.font, name,
                this.left + (BODY_WIDTH - this.font.width(name)) / 2,
                this.top + 3, this.playerPreview ? PEHKUI_TEXT : TITLE_TEXT, false);
        graphics.drawString(this.font, Component.translatable("pocket.screen.shrinkray.scale"),
                this.left + SCALE_LABEL_LEFT, this.top + SCALE_LABEL_TOP, 0xFFFFFF, false);
        if (this.shrinkRayMenu || this.playerPreview) {
            graphics.drawString(this.font, Component.translatable(this.playerPreview
                            ? "pocket.screen.personal.lock"
                            : "pocket.screen.shrinkray.targeting"),
                    this.left + TARGETING_LABEL_LEFT, this.top + TARGETING_LABEL_TOP, LABEL_TEXT, false);
        }

        renderShrinkRay(graphics);
    }

    private void renderScalePreview(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        final int x0 = this.left + PREVIEW_LEFT;
        final int y0 = this.top + PREVIEW_TOP;
        final int x1 = x0 + PREVIEW_WIDTH;
        final int y1 = y0 + PREVIEW_HEIGHT;
        final double selectedScale = Mth.clamp(easePreview(), BOARD_MIN, BOARD_MAX);

        final double groundY = y1 - PREVIEW_GROUND_INSET;

        graphics.enableScissor(x0, y0, x1, y1);
        try {
            graphics.fill(x0, y0, x1, y1, PREVIEW_BACKGROUND);

            final PlayerSkin skin = player.getSkin();
            final int arm = skin.model() == PlayerSkin.Model.SLIM ? 3 : 4;
            if (this.playerPreview) {
                renderPlayerScalePreview(graphics, skin, arm, x0, x1, groundY, selectedScale);
                return;
            }

            final double pixelsPerBlock = previewPixelsPerBlock(selectedScale);
            final double tilePixels = selectedScale * pixelsPerBlock;
            final double unit = pixelsPerBlock * PREVIEW_PLAYER_HEIGHT_BLOCKS / 32.0D;
            final double playerWidth = (8 + arm * 2) * unit;

            final double centreX = x0 + PREVIEW_WIDTH * 0.5D;
            final double playerStageEdge = centreX - PREVIEW_CENTRE_GAP * 0.5D;
            final double tileStageEdge = centreX + PREVIEW_CENTRE_GAP * 0.5D;

            final double canonicalUnit = PREVIEW_CANONICAL_PIXELS_PER_BLOCK
                    * PREVIEW_PLAYER_HEIGHT_BLOCKS / 32.0D;
            final double canonicalPlayerWidth = (8 + arm * 2) * canonicalUnit;
            final double canonicalPlayerCentreX = playerStageEdge - canonicalPlayerWidth * 0.5D;
            final double edgeAnchoredPlayerCentreX = playerStageEdge - playerWidth * 0.5D;
            final double basePlayerCentreX = Math.min(canonicalPlayerCentreX, edgeAnchoredPlayerCentreX);

            final double canonicalTileCentreX = tileStageEdge
                    + PREVIEW_CANONICAL_PIXELS_PER_BLOCK * 0.5D;
            final double centredTileX = canonicalTileCentreX - tilePixels * 0.5D;
            final double unsnappedTileX = Math.max(tileStageEdge, centredTileX);

            final double tinyProgress = previewTinyProgress(selectedScale);
            final double tinyPlayerNudge = tinyProgress
                    * PREVIEW_TINY_PLAYER_FOOT_NUDGE_TEXELS * unit;
            final double playerCentreX = basePlayerCentreX + tinyPlayerNudge;
            final double groundAnchorX = tileStageEdge + tinyPlayerNudge;
            final double tileX = unsnappedTileX + powerOfTwoGridCorrection(selectedScale) * pixelsPerBlock;

            renderPreviewGround(graphics, x0, x1, groundAnchorX, groundY,
                    PREVIEW_GROUND_INSET, pixelsPerBlock);
            renderPreviewPlayer(graphics, skin, arm, playerCentreX, groundY, unit);
            renderPreviewTile(graphics, tileX, groundY, tilePixels);
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderPlayerScalePreview(
            final GuiGraphics graphics,
            final PlayerSkin skin,
            final int arm,
            final int x0,
            final int x1,
            final double groundY,
            final double selectedScale
    ) {
        final double pixelsPerBlock = previewPixelsPerBlock(selectedScale, PREVIEW_PLAYER_HEIGHT_BLOCKS);
        final double unit = pixelsPerBlock * selectedScale * PREVIEW_PLAYER_HEIGHT_BLOCKS / 32.0D;
        final double playerWidth = (8 + arm * 2) * unit;

        final double centreX = x0 + PREVIEW_WIDTH * 0.5D;
        final double blockStageEdge = centreX - PREVIEW_CENTRE_GAP * 0.5D;
        final double playerStageEdge = centreX + PREVIEW_CENTRE_GAP * 0.5D;

        final double canonicalPlayerWidth = (8 + arm * 2)
                * PREVIEW_CANONICAL_PIXELS_PER_BLOCK * PREVIEW_PLAYER_HEIGHT_BLOCKS / 32.0D;
        final double canonicalPlayerCentreX = playerStageEdge + canonicalPlayerWidth * 0.5D;
        final double playerLeft = Math.min(
                Math.max(playerStageEdge, canonicalPlayerCentreX - playerWidth * 0.5D),
                x1 - PREVIEW_PLAYER_RIGHT_MARGIN - playerWidth);

        renderPreviewGround(graphics, x0, x1, blockStageEdge, groundY, PREVIEW_GROUND_INSET, pixelsPerBlock);
        renderPreviewTile(graphics, blockStageEdge - pixelsPerBlock, groundY, pixelsPerBlock);
        renderPreviewPlayer(graphics, skin, arm, playerLeft + playerWidth * 0.5D, groundY, unit);
    }

    private static double previewPixelsPerBlock(final double scale) {
        return previewPixelsPerBlock(scale, 1.0D);
    }

    private static double previewPixelsPerBlock(final double scale, final double subjectHeightBlocks) {
        double pixelsPerBlock = PREVIEW_CANONICAL_PIXELS_PER_BLOCK;

        if (scale < PREVIEW_FIXED_CAMERA_MIN_SCALE) {
            final double cameraZoom = Math.exp(
                    Math.log(PREVIEW_TINY_CAMERA_AT_MIN) * previewTinyProgress(scale));
            pixelsPerBlock *= cameraZoom;
        }

        final double maxTargetPixels = PREVIEW_HEIGHT
                - PREVIEW_GROUND_INSET
                - PREVIEW_TARGET_TOP_MARGIN;
        pixelsPerBlock = Math.min(pixelsPerBlock, maxTargetPixels / (scale * subjectHeightBlocks));

        return pixelsPerBlock;
    }

    private static double previewTinyProgress(final double scale) {
        if (scale >= PREVIEW_FIXED_CAMERA_MIN_SCALE) return 0.0D;
        if (scale <= PREVIEW_MIN_SCALE) return 1.0D;

        final double totalOctaves = log2(PREVIEW_FIXED_CAMERA_MIN_SCALE / PREVIEW_MIN_SCALE);
        final double currentOctaves = log2(PREVIEW_FIXED_CAMERA_MIN_SCALE / scale);
        return smootherstep(Mth.clamp(currentOctaves / totalOctaves, 0.0D, 1.0D));
    }

    private static double tileGridOffsetBlocks(final double scale) {
        final double pixelsPerBlock = previewPixelsPerBlock(scale);
        final double tilePixels = scale * pixelsPerBlock;
        final double centred = Math.max(0.0D, PREVIEW_CANONICAL_PIXELS_PER_BLOCK * 0.5D - tilePixels * 0.5D);
        final double nudge = previewTinyProgress(scale) * PREVIEW_TINY_PLAYER_FOOT_NUDGE_TEXELS
                * pixelsPerBlock * PREVIEW_PLAYER_HEIGHT_BLOCKS / 32.0D;
        return (centred - nudge) / pixelsPerBlock;
    }

    private static double gridCorrectionAt(final double powerOfTwo) {
        final double offset = tileGridOffsetBlocks(powerOfTwo);
        return Math.round(offset * 16.0D) / 16.0D - offset;
    }

    private static double powerOfTwoGridCorrection(final double scale) {
        final double exponent = log2(scale);
        final double below = Math.floor(exponent);
        final double t = exponent - below;
        return Mth.lerp(t, gridCorrectionAt(Math.pow(2.0D, below)), gridCorrectionAt(Math.pow(2.0D, below + 1.0D)));
    }

    private double easePreview() {
        final long now = System.nanoTime();
        final double target = Mth.clamp(this.value, BOARD_MIN, BOARD_MAX);
        if (Double.isNaN(this.previewValue) || Double.isNaN(this.previewTo) || this.dragging) {
            this.previewValue = this.previewFrom = this.previewTo = target;
            return target;
        }
        if (!ScaleBounds.same(target, this.previewTo)) {
            this.previewFrom = this.previewValue;
            this.previewTo = target;
            this.previewStart = now;
        }
        final double t = Math.min(1.0D, (now - this.previewStart) / 1.0E9D / PREVIEW_EASE_SECONDS);
        final double eased = 1.0D - Math.pow(1.0D - t, 3.0D);
        this.previewValue = Math.pow(2.0D, Mth.lerp(eased, log2(this.previewFrom), log2(this.previewTo)));
        return this.previewValue;
    }

    private static double smootherstep(final double t) {
        return t * t * t * (t * (t * 6.0D - 15.0D) + 10.0D);
    }

    private void renderPreviewGround(
            final GuiGraphics graphics,
            final int x0,
            final int x1,
            final double anchorX,
            final double groundY,
            final double groundInset,
            final double pixelsPerBlock
    ) {
        final double blockPixels = Math.max(1.0D, pixelsPerBlock);
        final TextureAtlasSprite side = sideSprite(PREVIEW_GROUND_BLOCK.get().defaultBlockState());
        final int first = (int) Math.floor((x0 - anchorX) / blockPixels);
        final int last = (int) Math.ceil((x1 - anchorX) / blockPixels);

        for (int i = first; i <= last; i++) {
            final double tileX = anchorX + i * blockPixels;
            for (double tileY = groundY; tileY < groundY + groundInset; tileY += blockPixels) {
                blitSprite(graphics, side, tileX, tileY, 0.0F, blockPixels);
            }
        }
    }

    private static TextureAtlasSprite sideSprite(final BlockState state) {
        final BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        final List<BakedQuad> quads = model.getQuads(state, Direction.NORTH, RandomSource.create(42L));
        return quads.isEmpty() ? model.getParticleIcon() : quads.get(0).getSprite();
    }

    private static void blitSprite(
            final GuiGraphics graphics,
            final TextureAtlasSprite sprite,
            final double x,
            final double y,
            final float z,
            final double size
    ) {
        final PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale((float) (size / 16.0D), (float) (size / 16.0D), 1.0F);
        graphics.blit(0, 0, 0, 16, 16, sprite);
        pose.popPose();
    }

    private void renderPreviewPlayer(
            final GuiGraphics graphics,
            final PlayerSkin skin,
            final int arm,
            final double centreX,
            final double groundY,
            final double unit
    ) {
        final ResourceLocation texture = skin.texture();

        final PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centreX, groundY - 32.0D * unit, 0.0D);
        pose.scale((float) unit, (float) unit, 1.0F);
        RenderSystem.enableBlend();
        final int[][] parts = {
                {8, 8, -4, 0, 8, 8}, {20, 20, -4, 8, 8, 12},
                {44, 20, -4 - arm, 8, arm, 12}, {36, 52, 4, 8, arm, 12},
                {4, 20, -4, 20, 4, 12}, {20, 52, 0, 20, 4, 12},
                {40, 8, -4, 0, 8, 8}, {20, 36, -4, 8, 8, 12},
                {44, 36, -4 - arm, 8, arm, 12}, {52, 52, 4, 8, arm, 12},
                {4, 36, -4, 20, 4, 12}, {4, 52, 0, 20, 4, 12}};
        for (final int[] part : parts) {
            graphics.blit(texture, part[2], part[3], part[0], part[1], part[4], part[5], 64, 64);
        }
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private void renderPreviewTile(
            final GuiGraphics graphics,
            final double anchorX,
            final double groundY,
            final double tilePixels
    ) {
        final double itemX = anchorX;
        final double itemY = groundY - tilePixels;

        blitSprite(graphics, sideSprite(Blocks.CRAFTING_TABLE.defaultBlockState()),
                itemX, itemY, 50.0F, tilePixels);
    }

    private void renderShrinkRay(final GuiGraphics graphics) {
        final ItemStack stack = held();
        if (stack == null) return;

        GuiGameElement.of(stack)
                .scale(4)
                .at(
                        this.left + this.background.getWidth(),
                        this.top + this.background.getHeight() - 48,
                        -200)
                .render(graphics);
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTicks) {
        if (this.fieldWasFocused && !this.field.isFocused()) commitField();
        this.fieldWasFocused = this.field.isFocused();

        super.render(graphics, mouseX, mouseY, partialTicks);
        renderBoard(graphics);
        renderNotToScale(graphics);

        if (this.field != null && this.field.isMouseOver(mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                    this.font,
                    scaleFieldTooltip(),
                    mouseX,
                    mouseY);
        }
    }

    private List<Component> scaleFieldTooltip() {
        return List.of(
                Component.translatable("pocket.screen.shrinkray.scale")
                        .plainCopy()
                        .withStyle(style -> style.withColor(AbstractSimiWidget.HEADER_RGB.getRGB())),
                CreateLang.translateDirect("gui.scrollInput.scrollToModify")
                        .plainCopy()
                        .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY),
                CreateLang.translateDirect("gui.scrollInput.shiftScrollsFaster")
                        .plainCopy()
                        .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY),
                Component.translatable("pocket.screen.shrinkray.ctrl_scrolls_slower")
                        .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
    }

    private void renderNotToScale(final GuiGraphics graphics) {
        if (this.value >= BOARD_MIN - ScaleBounds.EPSILON && this.value <= BOARD_MAX + ScaleBounds.EPSILON) return;
        final Component text = Component.translatable("pocket.screen.shrinkray.not_to_scale");
        final PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(this.left + PREVIEW_LEFT + PREVIEW_WIDTH * 0.5D, this.top + PREVIEW_TOP + 3.0D, 400.0D);
        pose.scale(NOT_TO_SCALE_SIZE, NOT_TO_SCALE_SIZE, 1.0F);
        final int width = this.font.width(text);
        graphics.fill(-width / 2 - 2, -2, width - width / 2 + 2, this.font.lineHeight, 0x50000000);
        graphics.drawString(this.font, text, -width / 2, 0, 0xFFFFFF, true);
        pose.popPose();
    }

    private PocketValueSettingsTexture.Sheet value(final PocketValueSettingsTexture part) {
        return part.on(this.playerPreview ? PocketValueSettingsTexture.PEHKUI : part.getLocation());
    }

    private void renderBoard(final GuiGraphics graphics) {
        final int barX = this.left + BAR_LEFT;
        final int barY = this.top + BAR_TOP;

        purpleFrame(graphics, barX - 4, barY - 3, BAR_WIDTH + 8, BAR_HEIGHT + 6);
        UIRenderHelper.drawStretched(graphics, barX - 1, barY - 1, BAR_WIDTH + 2, BAR_HEIGHT + 2, 0,
                value(PocketValueSettingsTexture.BAR_BG));
        UIRenderHelper.drawCropped(graphics, barX, barY, BAR_WIDTH, BAR_HEIGHT, 0,
                value(PocketValueSettingsTexture.BAR));
        for (final double stop : this.stops) {
            value(PocketValueSettingsTexture.MILESTONE).render(graphics, xOf(stop) - 3, barY);
        }

        final String text = HudScaleText.bare(this.value);
        final int width = (this.font.width(text) / 2) * 2 + 3;
        final int cursorX = Mth.clamp(xOf(this.value) - width / 2, barX, barX + BAR_WIDTH - width);
        final int cursorY = barY - 3;
        value(PocketValueSettingsTexture.CURSOR_LEFT).render(graphics, cursorX - 3, cursorY);
        UIRenderHelper.drawCropped(graphics, cursorX, cursorY, width, 14, 0, value(PocketValueSettingsTexture.CURSOR));
        value(PocketValueSettingsTexture.CURSOR_RIGHT).render(graphics, cursorX + width, cursorY);
        graphics.drawString(this.font, text, cursorX + 2, cursorY + 3, this.playerPreview ? PEHKUI_TEXT : CURSOR_TEXT, false);
    }

    private void purpleFrame(final GuiGraphics graphics, final int x, final int y, final int w, final int h) {
        value(PocketValueSettingsTexture.FRAME_TL).render(graphics, x, y);
        value(PocketValueSettingsTexture.FRAME_TR).render(graphics, x + w - 4, y);
        value(PocketValueSettingsTexture.FRAME_BL).render(graphics, x, y + h - 4);
        value(PocketValueSettingsTexture.FRAME_BR).render(graphics, x + w - 4, y + h - 4);
        UIRenderHelper.drawStretched(graphics, x, y + 4, 3, h - 8, 0, value(PocketValueSettingsTexture.FRAME_LEFT));
        UIRenderHelper.drawStretched(graphics, x + w - 3, y + 4, 3, h - 8, 0, value(PocketValueSettingsTexture.FRAME_RIGHT));
        UIRenderHelper.drawCropped(graphics, x + 4, y, w - 8, 3, 0, value(PocketValueSettingsTexture.FRAME_TOP));
        UIRenderHelper.drawCropped(graphics, x + 4, y + h - 3, w - 8, 3, 0, value(PocketValueSettingsTexture.FRAME_BOTTOM));
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (this.field.isFocused() && !this.field.isMouseOver(mouseX, mouseY) && !overBoard(mouseX, mouseY)) {
            releaseField(true);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && overBoard(mouseX, mouseY)) {
            releaseField(false);
            this.dragging = true;
            set(Screen.hasControlDown() ? freeAt(mouseX) : stopAt(mouseX));
            return true;
        }
        final boolean wasFocused = this.field.isFocused();
        final boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (!wasFocused && this.field.isFocused()) {
            showField(fieldText(this.value));
            this.field.moveCursorToEnd(false);
            this.field.setHighlightPos(0);
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button,
                                final double dragX, final double dragY) {
        if (!this.dragging) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        set(Screen.hasControlDown() ? freeAt(mouseX) : stopAt(mouseX));
        return true;
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
        if (this.dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (scrollY == 0.0D)
            return false;

        final boolean overField =
                this.field != null && this.field.isMouseOver(mouseX, mouseY);

        final boolean overScaleBoard =
                overBoard(mouseX, mouseY);

        if (!overField && !overScaleBoard)
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

        releaseField(true);

        final int direction = scrollY > 0.0D ? 1 : -1;

        if (overField && Screen.hasControlDown()) {
            set(nudge(direction));
            return true;
        }

        if (overField) {
            set(Screen.hasShiftDown()
                    ? step(direction)
                    : stepField(direction));

            return true;
        }

        set(Screen.hasControlDown() ? step(this.extendedDetents, direction) : step(direction));
        return true;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        final boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (this.field.isFocused()) {
            if (enter || key == GLFW.GLFW_KEY_ESCAPE) {
                releaseField(true);
                return true;
            }
            return super.keyPressed(key, scanCode, modifiers);
        }

        if (enter) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_LEFT) {
            set(step(key == GLFW.GLFW_KEY_RIGHT ? 1 : -1));
            return true;
        }
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(key, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(final char c, final int modifiers) {
        if (!this.field.isFocused() && (Character.isDigit(c) || c == '.' || c == ',')) {
            setFocused(this.field);
            this.field.setFocused(true);
            this.fieldWasFocused = true;
            this.field.setValue(c == ',' ? "." : String.valueOf(c));
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    private void typed(final String text) {
        if (this.syncing || !this.field.isFocused()) return;
        final double parsed = parse(text);
        if (this.range.contains(parsed)) set(parsed, false);
    }

    private void releaseField(final boolean commit) {
        if (!this.field.isFocused()) return;
        setFocused(null);
        this.field.setFocused(false);
        this.fieldWasFocused = false;
        if (commit) {
            commitField();
        } else {
            syncField();
        }
    }

    private void commitField() {
        final double parsed = parse(this.field.getValue());
        if (!Double.isNaN(parsed)) {
            set(ScaleBounds.isValid(parsed) ? this.range.clamp(parsed) : this.range.min(), false);
        }
        syncField();
    }

    private void set(final double next) {
        set(next, true);
    }

    private void set(final double next, final boolean syncField) {
        final boolean changed = !ScaleBounds.same(next, this.value);
        this.value = next;
        if (syncField && !this.field.isFocused()) syncField();
        if (changed && this.soundCooldown == 0 && this.minecraft != null) {
            final float pitch = Mth.lerp((float) (offset(next) - offset(BOARD_MIN)) / (offset(BOARD_MAX) - offset(BOARD_MIN)),
                    1.15F, 1.5F);
            this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(AllSoundEvents.SCROLL_VALUE.getMainEvent(), pitch, 0.25F));
            this.soundCooldown = 1;
        }
    }

    private void syncField() {
        if (this.field == null) return;
        showField(fieldText(this.value));
        this.field.moveCursorToStart(false);
    }

    private static String fieldText(final double scale) {
        if (scale >= 0.001D - ScaleBounds.EPSILON) return String.format(Locale.ROOT, "%.3f", scale);
        return new java.math.BigDecimal(scale).round(new java.math.MathContext(3)).stripTrailingZeros().toPlainString();
    }

    private void showField(final String text) {
        this.syncing = true;
        this.field.setValue(text);
        this.field.setTextColor(TEXT);
        this.syncing = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        final LocalPlayer player = Minecraft.getInstance().player;
        final ItemStack stack = held();
        if (player == null || stack == null) return;
        if (this.field != null && this.field.isFocused()) commitField();

        final ScaleSelectingItem tool = (ScaleSelectingItem) stack.getItem();
        if (!tool.permitsSelection(player, this.value)) return;

        tool.select(stack, this.value);

        if (stack.getItem() instanceof CreativeShrinkRayItem) {
            CreativeShrinkRayItem.setTargetingMode(stack, this.targeting);
            PacketDistributor.sendToServer(new ShrinkRaySettingsPayload(this.hand, this.value, this.targeting.ordinal()));
        } else {
            PacketDistributor.sendToServer(new ShrinkRayScalePayload(this.hand, this.value));
        }
        if (stack.getItem() instanceof SelfResizeDeviceItem) {
            PersonalLock.setClient(this.unlocked);
            PacketDistributor.sendToServer(new PersonalLockPayload(this.unlocked));
        }
    }

    private ItemStack held() {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;
        final ItemStack stack = player.getItemInHand(this.hand);
        return stack.getItem() instanceof ScaleSelectingItem ? stack : null;
    }

    private static int offset(final double scale) {
        final double clamped = Mth.clamp(scale, BOARD_MIN, BOARD_MAX);
        final double along = clamped <= ScaleBounds.FULL
                ? log2(clamped) * BELOW_SPACING
                : (clamped - ScaleBounds.FULL) * ABOVE_SPACING;
        return CENTRE + (int) Math.round(along);
    }

    private int xOf(final double scale) {
        return this.left + BAR_LEFT + offset(scale);
    }

    private double stopAt(final double mouseX) {
        if (this.detents.length == 0) return this.value;
        final double barX = this.left + BAR_LEFT;
        final boolean past = mouseX > barX + offset(BOARD_MAX) || mouseX < barX + offset(BOARD_MIN);
        if (past) {
            final double target = Math.log(valueAt(mouseX));
            int best = 0;
            for (int i = 1; i < this.detents.length; i++) {
                if (Math.abs(Math.log(this.detents[i]) - target) < Math.abs(Math.log(this.detents[best]) - target)) best = i;
            }
            return this.detents[best];
        }
        int best = -1;
        for (int i = 0; i < this.detents.length; i++) {
            final double stop = this.detents[i];
            if (stop < BOARD_MIN - ScaleBounds.EPSILON || stop > BOARD_MAX + ScaleBounds.EPSILON) continue;
            if (best < 0 || Math.abs(xOf(stop) - mouseX) < Math.abs(xOf(this.detents[best]) - mouseX)) best = i;
        }
        return best < 0 ? this.value : this.detents[best];
    }

    private double freeAt(final double mouseX) {
        final double scale = valueAt(mouseX);
        return this.range.clamp(round(scale, scale < 0.1D ? 1000.0D : 100.0D));
    }

    private double valueAt(final double mouseX) {
        final double barX = this.left + BAR_LEFT;
        final double maxX = barX + offset(BOARD_MAX);
        final double minX = barX + offset(BOARD_MIN);
        if (mouseX > maxX) return this.range.clamp(BOARD_MAX * Math.pow(2.0D, (mouseX - maxX) / BELOW_SPACING));
        if (mouseX < minX) return this.range.clamp(BOARD_MIN * Math.pow(2.0D, (mouseX - minX) / BELOW_SPACING));
        final double along = mouseX - barX - CENTRE;
        final double scale = along <= 0.0D
                ? Math.pow(2.0D, along / BELOW_SPACING)
                : ScaleBounds.FULL + along / ABOVE_SPACING;
        return this.range.clamp(Mth.clamp(scale, BOARD_MIN, BOARD_MAX));
    }

    private double nudge(final int direction) {
        final boolean fine = direction > 0 ? this.value < 0.1D - ScaleBounds.EPSILON : this.value <= 0.1D + ScaleBounds.EPSILON;
        final double unit = fine ? 1000.0D : 100.0D;
        final double next = round(this.value, unit) + direction / unit;
        return this.range.clamp(round(next, unit));
    }

    private static double round(final double scale, final double unit) {
        return Math.round(scale * unit) / unit;
    }

    private double step(final int direction) {
        return step(this.detents, direction);
    }

    private double step(final double[] stops, final int direction) {
        if (direction > 0) {
            for (final double stop : stops) {
                if (stop > this.value + ScaleBounds.EPSILON) return stop;
            }
            return this.value;
        }
        for (int i = stops.length - 1; i >= 0; i--) {
            if (stops[i] < this.value - ScaleBounds.EPSILON) return stops[i];
        }
        return this.value;
    }

    private boolean overBoard(final double mouseX, final double mouseY) {
        final int barX = this.left + BAR_LEFT;
        final int barY = this.top + BAR_TOP;
        return mouseX >= barX - 4 && mouseX <= barX + BAR_WIDTH + 4
                && mouseY >= barY - 5 && mouseY <= barY + BAR_HEIGHT + 5;
    }

    static double parse(final String raw) {
        String text = raw.trim().toLowerCase(Locale.ROOT).replace(',', '.').replace(" ", "");
        double factor = 1.0D;
        if (text.endsWith("%")) {
            factor = 0.01D;
            text = text.substring(0, text.length() - 1);
        }
        while (text.endsWith("x") || text.endsWith("×")) text = text.substring(0, text.length() - 1);
        if (text.isEmpty()) return Double.NaN;
        try {
            final int slash = text.indexOf('/');
            if (slash < 0) return Double.parseDouble(text) * factor;
            final double numerator = Double.parseDouble(text.substring(0, slash));
            final double denominator = Double.parseDouble(text.substring(slash + 1));
            return denominator == 0.0D ? Double.NaN : numerator / denominator * factor;
        } catch (final NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static double log2(final double value) {
        return Math.log(value) / Math.log(2.0D);
    }
}
