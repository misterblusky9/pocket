package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.CreativeShrinkRayItem;
import com.misterblusky9.pocket.item.ScaleSelectingItem;
import com.misterblusky9.pocket.item.SelfResizeDeviceItem;
import com.misterblusky9.pocket.config.DeviceRanges;
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
    private static final double BOARD_MIN = 1.0D / 32.0D;
    private static final double BOARD_MAX = 8.0D;
    private static final double[] EXTENDED_STOPS = {1.0D / 64.0D, 16.0D};
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
    private static final int BELOW_SPACING = 10;
    private static final int ABOVE_SPACING = 8;
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
    private static final Supplier<Block> PREVIEW_SUBSOIL_BLOCK = () -> Blocks.DIRT;

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
    private static final ResourceLocation PIG_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/pig/pig.png");

    private boolean playerPreview;
    private boolean pvpToggleVisible;
    private boolean unlocked;

    public ScaleSelectionScreen(InteractionHand hand) {
        super(Component.translatable("pocket.screen.scale_selection"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        LocalPlayer player = Minecraft.getInstance().player;
        ItemStack stack = held();
        if (player == null || stack == null) {
            onClose();
            return;
        }
        ScaleSelectingItem tool = (ScaleSelectingItem) stack.getItem();

        applyRange(tool.selectionRange(stack, player));

        this.initial = tool.selection(stack, player);
        this.value = ScaleBounds.isValid(this.initial) ? this.initial : ScaleBounds.FULL;
        this.previewValue = this.value;
        this.shrinkRayMenu = stack.getItem() instanceof CreativeShrinkRayItem;
        this.playerPreview = stack.getItem() instanceof SelfResizeDeviceItem;
        this.background = this.playerPreview ? PocketGuiTexture.PERSONAL_COMPRESSOR : PocketGuiTexture.SHRINKRAY;
        if (this.shrinkRayMenu) {
            this.targeting = CreativeShrinkRayItem.targetingMode(stack);
        }
        if (this.playerPreview) {
            this.unlocked = PersonalLock.unlocked(player);
            this.pvpToggleVisible = PersonalLock.canToggle();
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

        IconButton confirm = new IconButton(this.left + CONFIRM_LEFT, this.top + TARGET_BUTTON_TOP, AllIcons.I_CONFIRM);
        confirm.withCallback(this::onClose);
        addRenderableWidget(confirm);

        IconButton reset = new IconButton(this.left + RESET_LEFT, this.top + TARGET_BUTTON_TOP, AllIcons.I_ROTATE_CCW);
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
        if (this.pvpToggleVisible) {
            addLockButton(0, false, AllIcons.I_CONFIG_LOCKED, "pocket.screen.personal.locked");
            addLockButton(1, true, AllIcons.I_CONFIG_UNLOCKED, "pocket.screen.personal.unlocked");
            refreshTargetingButtons();
        }
    }

    private void applyRange(ScaleBounds range) {
        this.range = range;
        this.stops = DoubleStream.concat(
                        IntStream.rangeClosed(-10, -1).mapToDouble(exponent -> Math.pow(2.0D, exponent)),
                        IntStream.rangeClosed(1, (int) BOARD_MAX).asDoubleStream())
                .filter(stop -> stop >= BOARD_MIN - ScaleBounds.EPSILON && stop <= BOARD_MAX + ScaleBounds.EPSILON)
                .filter(this.range::contains)
                .toArray();
        double[] seen = this.stops;
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
    }

    private void addLockButton(int index, boolean unlocked, AllIcons icon, String tooltip) {
        IconButton button = new IconButton(
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

    private double stepField(int direction) {
        return step(this.fieldDetents, direction);
    }

    private void addTargetingButton(
            int index,
            CreativeShrinkRayItem.TargetingMode mode,
            PocketIcon icon,
            String tooltip
    ) {
        IconButton button = new IconButton(
                this.left + TARGET_BUTTON_LEFT + index * 18,
                this.top + TARGET_BUTTON_TOP,
                icon);
        button.withCallback(() -> setTargeting(mode));
        button.setToolTip(Component.translatable(tooltip));
        this.targetingButtons.add(button);
        addRenderableWidget(button);
    }

    private void setTargeting(CreativeShrinkRayItem.TargetingMode mode) {
        this.targeting = mode;
        refreshTargetingButtons();
        applyRange(CreativeShrinkRayItem.limits(mode, Minecraft.getInstance().player));
        releaseField(false);
        set(this.range.clamp(this.value));
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
        if (this.pvpToggleVisible) {
            this.unlocked = DeviceRanges.pvpScalingMode().defaultAllowed();
            refreshTargetingButtons();
        }
    }

    @Override
    public void tick() {
        if (this.soundCooldown > 0) this.soundCooldown--;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderBackground(graphics, mouseX, mouseY, partialTicks);
        renderScalePreview(graphics, mouseX, mouseY);
        this.background.render(graphics, this.left, this.top);

        ItemStack stack = held();
        if (stack == null) return;
        Component name = stack.getHoverName();
        graphics.drawString(this.font, name,
                this.left + (BODY_WIDTH - this.font.width(name)) / 2,
                this.top + 3, this.playerPreview ? PEHKUI_TEXT : TITLE_TEXT, false);
        graphics.drawString(this.font, Component.translatable("pocket.screen.shrinkray.scale"),
                this.left + SCALE_LABEL_LEFT, this.top + SCALE_LABEL_TOP, 0xFFFFFF, false);
        if (this.shrinkRayMenu || this.pvpToggleVisible) {
            graphics.drawString(this.font, Component.translatable(this.playerPreview
                            ? "pocket.screen.personal.lock"
                            : "pocket.screen.shrinkray.targeting"),
                    this.left + TARGETING_LABEL_LEFT, this.top + TARGETING_LABEL_TOP, LABEL_TEXT, false);
        }

        renderShrinkRay(graphics);
    }

    private void renderScalePreview(GuiGraphics graphics, int mouseX, int mouseY) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        int x0 = this.left + PREVIEW_LEFT;
        int y0 = this.top + PREVIEW_TOP;
        int x1 = x0 + PREVIEW_WIDTH;
        int y1 = y0 + PREVIEW_HEIGHT;
        double selectedScale = Mth.clamp(easePreview(), BOARD_MIN, BOARD_MAX);

        double groundY = y1 - PREVIEW_GROUND_INSET;

        graphics.enableScissor(x0, y0, x1, y1);
        try {
            graphics.fill(x0, y0, x1, y1, PREVIEW_BACKGROUND);

            PlayerSkin skin = player.getSkin();
            int arm = skin.model() == PlayerSkin.Model.SLIM ? 3 : 4;
            if (this.playerPreview) {
                renderPlayerScalePreview(graphics, skin, arm, x0, x1, groundY, selectedScale);
                return;
            }

            double pixelsPerBlock = previewPixelsPerBlock(selectedScale);
            double tilePixels = selectedScale * pixelsPerBlock;
            double unit = pixelsPerBlock * PREVIEW_PLAYER_HEIGHT_BLOCKS / 32.0D;
            double playerWidth = (8 + arm * 2) * unit;

            double centreX = x0 + PREVIEW_WIDTH * 0.5D;
            double playerStageEdge = centreX - PREVIEW_CENTRE_GAP * 0.5D;
            double tileStageEdge = centreX + PREVIEW_CENTRE_GAP * 0.5D;

            double canonicalUnit = PREVIEW_CANONICAL_PIXELS_PER_BLOCK
                    * PREVIEW_PLAYER_HEIGHT_BLOCKS / 32.0D;
            double canonicalPlayerWidth = (8 + arm * 2) * canonicalUnit;
            double canonicalPlayerCentreX = playerStageEdge - canonicalPlayerWidth * 0.5D;
            double edgeAnchoredPlayerCentreX = playerStageEdge - playerWidth * 0.5D;
            double basePlayerCentreX = Math.min(canonicalPlayerCentreX, edgeAnchoredPlayerCentreX);

            double canonicalTileCentreX = tileStageEdge
                    + PREVIEW_CANONICAL_PIXELS_PER_BLOCK * 0.5D;
            double centredTileX = canonicalTileCentreX - tilePixels * 0.5D;
            double unsnappedTileX = Math.max(tileStageEdge, centredTileX);

            double tinyProgress = previewTinyProgress(selectedScale);
            double tinyPlayerNudge = tinyProgress
                    * PREVIEW_TINY_PLAYER_FOOT_NUDGE_TEXELS * unit;
            double playerCentreX = basePlayerCentreX + tinyPlayerNudge;
            double groundAnchorX = tileStageEdge + tinyPlayerNudge;
            double tileX = unsnappedTileX + powerOfTwoGridCorrection(selectedScale) * pixelsPerBlock;

            renderPreviewGround(graphics, x0, x1, groundAnchorX, groundY,
                    PREVIEW_GROUND_INSET, pixelsPerBlock);
            renderPreviewPlayer(graphics, skin, arm, playerCentreX, groundY, unit);
            renderPreviewTile(graphics, tileX, groundY, tilePixels);
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderPlayerScalePreview(
            GuiGraphics graphics,
            PlayerSkin skin,
            int arm,
            int x0,
            int x1,
            double groundY,
            double selectedScale
    ) {
        double pixelsPerBlock = previewPixelsPerBlock(selectedScale, PREVIEW_PLAYER_HEIGHT_BLOCKS);
        double unit = pixelsPerBlock * selectedScale * PREVIEW_PLAYER_HEIGHT_BLOCKS / 32.0D;
        double playerWidth = (8 + arm * 2) * unit;

        double centreX = x0 + PREVIEW_WIDTH * 0.5D;
        double blockStageEdge = centreX - PREVIEW_CENTRE_GAP * 0.5D;
        double playerStageEdge = centreX + PREVIEW_CENTRE_GAP * 0.5D;

        double canonicalPlayerWidth = (8 + arm * 2)
                * PREVIEW_CANONICAL_PIXELS_PER_BLOCK * PREVIEW_PLAYER_HEIGHT_BLOCKS / 32.0D;
        double canonicalPlayerCentreX = playerStageEdge + canonicalPlayerWidth * 0.5D;
        double playerLeft = Math.min(
                Math.max(playerStageEdge, canonicalPlayerCentreX - playerWidth * 0.5D),
                x1 - PREVIEW_PLAYER_RIGHT_MARGIN - playerWidth);

        renderPreviewGround(graphics, x0, x1, blockStageEdge, groundY, PREVIEW_GROUND_INSET, pixelsPerBlock);
        renderPreviewTile(graphics, blockStageEdge - pixelsPerBlock, groundY, pixelsPerBlock);
        renderPreviewPlayer(graphics, skin, arm, playerLeft + playerWidth * 0.5D, groundY, unit);
    }

    private static double previewPixelsPerBlock(double scale) {
        return previewPixelsPerBlock(scale, 1.0D);
    }

    private static double previewPixelsPerBlock(double scale, double subjectHeightBlocks) {
        double pixelsPerBlock = PREVIEW_CANONICAL_PIXELS_PER_BLOCK;

        if (scale < PREVIEW_FIXED_CAMERA_MIN_SCALE) {
            double cameraZoom = Math.exp(
                    Math.log(PREVIEW_TINY_CAMERA_AT_MIN) * previewTinyProgress(scale));
            pixelsPerBlock *= cameraZoom;
        }
        if (scale < PREVIEW_MIN_SCALE) pixelsPerBlock *= PREVIEW_MIN_SCALE / scale;

        double maxTargetPixels = PREVIEW_HEIGHT
                - PREVIEW_GROUND_INSET
                - PREVIEW_TARGET_TOP_MARGIN;
        pixelsPerBlock = Math.min(pixelsPerBlock, maxTargetPixels / (scale * subjectHeightBlocks));

        return pixelsPerBlock;
    }

    private static double previewTinyProgress(double scale) {
        if (scale >= PREVIEW_FIXED_CAMERA_MIN_SCALE) return 0.0D;
        if (scale <= PREVIEW_MIN_SCALE) return 1.0D;

        double totalOctaves = log2(PREVIEW_FIXED_CAMERA_MIN_SCALE / PREVIEW_MIN_SCALE);
        double currentOctaves = log2(PREVIEW_FIXED_CAMERA_MIN_SCALE / scale);
        return smootherstep(Mth.clamp(currentOctaves / totalOctaves, 0.0D, 1.0D));
    }

    private static double tileGridOffsetBlocks(double scale) {
        double pixelsPerBlock = previewPixelsPerBlock(scale);
        double tilePixels = scale * pixelsPerBlock;
        double centred = Math.max(0.0D, PREVIEW_CANONICAL_PIXELS_PER_BLOCK * 0.5D - tilePixels * 0.5D);
        double nudge = previewTinyProgress(scale) * PREVIEW_TINY_PLAYER_FOOT_NUDGE_TEXELS
                * pixelsPerBlock * PREVIEW_PLAYER_HEIGHT_BLOCKS / 32.0D;
        return (centred - nudge) / pixelsPerBlock;
    }

    private static double gridCorrectionAt(double powerOfTwo) {
        double offset = tileGridOffsetBlocks(powerOfTwo);
        return Math.round(offset * 16.0D) / 16.0D - offset;
    }

    private static double powerOfTwoGridCorrection(double scale) {
        double exponent = log2(scale);
        double below = Math.floor(exponent);
        double t = exponent - below;
        return Mth.lerp(t, gridCorrectionAt(Math.pow(2.0D, below)), gridCorrectionAt(Math.pow(2.0D, below + 1.0D)));
    }

    private double easePreview() {
        long now = System.nanoTime();
        double target = Mth.clamp(this.value, BOARD_MIN, BOARD_MAX);
        if (Double.isNaN(this.previewValue) || Double.isNaN(this.previewTo) || this.dragging) {
            this.previewValue = this.previewFrom = this.previewTo = target;
            return target;
        }
        if (!ScaleBounds.same(target, this.previewTo)) {
            this.previewFrom = this.previewValue;
            this.previewTo = target;
            this.previewStart = now;
        }
        double t = Math.min(1.0D, (now - this.previewStart) / 1.0E9D / PREVIEW_EASE_SECONDS);
        double eased = 1.0D - Math.pow(1.0D - t, 3.0D);
        this.previewValue = Math.pow(2.0D, Mth.lerp(eased, log2(this.previewFrom), log2(this.previewTo)));
        return this.previewValue;
    }

    private static double smootherstep(double t) {
        return t * t * t * (t * (t * 6.0D - 15.0D) + 10.0D);
    }

    private void renderPreviewGround(
            GuiGraphics graphics,
            int x0,
            int x1,
            double anchorX,
            double groundY,
            double groundInset,
            double pixelsPerBlock
    ) {
        double blockPixels = Math.max(1.0D, pixelsPerBlock);
        TextureAtlasSprite side = sideSprite(PREVIEW_GROUND_BLOCK.get().defaultBlockState());
        TextureAtlasSprite subsoil = sideSprite(PREVIEW_SUBSOIL_BLOCK.get().defaultBlockState());
        int first = (int) Math.floor((x0 - anchorX) / blockPixels);
        int last = (int) Math.ceil((x1 - anchorX) / blockPixels);

        for (int i = first; i <= last; i++) {
            double tileX = anchorX + i * blockPixels;
            for (double tileY = groundY; tileY < groundY + groundInset; tileY += blockPixels) {
                blitSprite(graphics, tileY == groundY ? side : subsoil, tileX, tileY, 0.0F, blockPixels);
            }
        }
    }

    private static TextureAtlasSprite sideSprite(BlockState state) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        List<BakedQuad> quads = model.getQuads(state, Direction.NORTH, RandomSource.create(42L));
        return quads.isEmpty() ? model.getParticleIcon() : quads.get(0).getSprite();
    }

    private static void blitSprite(
            GuiGraphics graphics,
            TextureAtlasSprite sprite,
            double x,
            double y,
            float z,
            double size
    ) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale((float) (size / 16.0D), (float) (size / 16.0D), 1.0F);
        graphics.blit(0, 0, 0, 16, 16, sprite);
        pose.popPose();
    }

    private void renderPreviewPlayer(
            GuiGraphics graphics,
            PlayerSkin skin,
            int arm,
            double centreX,
            double groundY,
            double unit
    ) {
        ResourceLocation texture = skin.texture();

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centreX, groundY - 32.0D * unit, 0.0D);
        pose.scale((float) unit, (float) unit, 1.0F);
        RenderSystem.enableBlend();
        int[][] parts = {
                {8, 8, -4, 0, 8, 8}, {20, 20, -4, 8, 8, 12},
                {44, 20, -4 - arm, 8, arm, 12}, {36, 52, 4, 8, arm, 12},
                {4, 20, -4, 20, 4, 12}, {20, 52, 0, 20, 4, 12},
                {40, 8, -4, 0, 8, 8}, {20, 36, -4, 8, 8, 12},
                {44, 36, -4 - arm, 8, arm, 12}, {52, 52, 4, 8, arm, 12},
                {4, 36, -4, 20, 4, 12}, {4, 52, 0, 20, 4, 12}};
        for (int[] part : parts) {
            graphics.blit(texture, part[2], part[3], part[0], part[1], part[4], part[5], 64, 64);
        }
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private void renderPreviewTile(
            GuiGraphics graphics,
            double anchorX,
            double groundY,
            double tilePixels
    ) {
        double itemX = anchorX;
        double itemY = groundY - tilePixels;

        if (this.shrinkRayMenu && this.targeting == CreativeShrinkRayItem.TargetingMode.ENTITIES) {
            renderPreviewPig(graphics, itemX + tilePixels * 0.5D, groundY, tilePixels / 16.0D);
            return;
        }
        blitSprite(graphics, sideSprite(Blocks.CRAFTING_TABLE.defaultBlockState()),
                itemX, itemY, 50.0F, tilePixels);
    }

    private void renderPreviewPig(
            GuiGraphics graphics,
            double centreX,
            double groundY,
            double unit
    ) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centreX, groundY - 16.0D * unit, 50.0D);
        pose.scale((float) unit, (float) unit, 1.0F);
        int[][] parts = {
                {4, 20, -5, 10, 4, 6}, {4, 20, 1, 10, 4, 6},
                {46, 8, -5, 3, 10, 8},
                {8, 8, -4, 0, 8, 8},
                {17, 17, -2, 4, 4, 3}};
        for (int[] part : parts) {
            graphics.blit(PIG_TEXTURE, part[2], part[3], part[0], part[1], part[4], part[5], 64, 32);
        }
        pose.popPose();
    }

    private void renderShrinkRay(GuiGraphics graphics) {
        ItemStack stack = held();
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
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

    private void renderNotToScale(GuiGraphics graphics) {
        if (this.value >= BOARD_MIN - ScaleBounds.EPSILON && this.value <= BOARD_MAX + ScaleBounds.EPSILON) return;
        Component text = Component.translatable("pocket.screen.shrinkray.not_to_scale");
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(this.left + PREVIEW_LEFT + PREVIEW_WIDTH * 0.5D, this.top + PREVIEW_TOP + 3.0D, 400.0D);
        pose.scale(NOT_TO_SCALE_SIZE, NOT_TO_SCALE_SIZE, 1.0F);
        int width = this.font.width(text);
        graphics.fill(-width / 2 - 2, -2, width - width / 2 + 2, this.font.lineHeight, 0x50000000);
        graphics.drawString(this.font, text, -width / 2, 0, 0xFFFFFF, true);
        pose.popPose();
    }

    private PocketValueSettingsTexture.Sheet value(PocketValueSettingsTexture part) {
        return part.on(this.playerPreview ? PocketValueSettingsTexture.PEHKUI : part.getLocation());
    }

    private void renderBoard(GuiGraphics graphics) {
        int barX = this.left + BAR_LEFT;
        int barY = this.top + BAR_TOP;

        purpleFrame(graphics, barX - 4, barY - 3, BAR_WIDTH + 8, BAR_HEIGHT + 6);
        UIRenderHelper.drawStretched(graphics, barX - 1, barY - 1, BAR_WIDTH + 2, BAR_HEIGHT + 2, 0,
                value(PocketValueSettingsTexture.BAR_BG));
        UIRenderHelper.drawCropped(graphics, barX, barY, BAR_WIDTH, BAR_HEIGHT, 0,
                value(PocketValueSettingsTexture.BAR));
        for (double stop : this.stops) {
            value(PocketValueSettingsTexture.MILESTONE).render(graphics, xOf(stop) - 3, barY);
        }

        Component text = this.value > BOARD_MAX + ScaleBounds.EPSILON ? Component.literal(">").withStyle(ChatFormatting.BOLD)
                : this.value < BOARD_MIN - ScaleBounds.EPSILON ? Component.literal("<").withStyle(ChatFormatting.BOLD)
                : Component.literal(HudScaleText.bare(this.value));
        int width = (this.font.width(text) / 2) * 2 + 3;
        int cursorX = this.value > BOARD_MAX + ScaleBounds.EPSILON ? barX + BAR_WIDTH - width
                : this.value < BOARD_MIN - ScaleBounds.EPSILON ? barX
                : Mth.clamp(xOf(this.value) - width / 2, barX, barX + BAR_WIDTH - width);
        int cursorY = barY - 3;
        value(PocketValueSettingsTexture.CURSOR_LEFT).render(graphics, cursorX - 3, cursorY);
        UIRenderHelper.drawCropped(graphics, cursorX, cursorY, width, 14, 0, value(PocketValueSettingsTexture.CURSOR));
        value(PocketValueSettingsTexture.CURSOR_RIGHT).render(graphics, cursorX + width, cursorY);
        graphics.drawString(this.font, text, cursorX + 2, cursorY + 3, this.playerPreview ? PEHKUI_TEXT : CURSOR_TEXT, false);
    }

    private void purpleFrame(GuiGraphics graphics, int x, int y, int w, int h) {
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.field.isFocused() && !this.field.isMouseOver(mouseX, mouseY) && !overBoard(mouseX, mouseY)) {
            releaseField(true);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && overBoard(mouseX, mouseY)) {
            releaseField(false);
            this.dragging = true;
            set(Screen.hasControlDown() ? freeAt(mouseX) : stopAt(mouseX));
            return true;
        }
        boolean wasFocused = this.field.isFocused();
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (!wasFocused && this.field.isFocused()) {
            showField(fieldText(this.value));
            this.field.moveCursorToEnd(false);
            this.field.setHighlightPos(0);
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (!this.dragging) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        set(Screen.hasControlDown() ? freeAt(mouseX) : stopAt(mouseX));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0D)
            return false;

        boolean overField =
                this.field != null && this.field.isMouseOver(mouseX, mouseY);

        boolean overScaleBoard =
                overBoard(mouseX, mouseY);

        if (!overField && !overScaleBoard)
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

        releaseField(true);

        int direction = scrollY > 0.0D ? 1 : -1;

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
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
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
    public boolean charTyped(char c, int modifiers) {
        if (!this.field.isFocused() && (Character.isDigit(c) || c == '.' || c == ',')) {
            setFocused(this.field);
            this.field.setFocused(true);
            this.fieldWasFocused = true;
            this.field.setValue(c == ',' ? "." : String.valueOf(c));
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    private void typed(String text) {
        if (this.syncing || !this.field.isFocused()) return;
        double parsed = parse(text);
        if (this.range.contains(parsed)) set(parsed, false);
    }

    private void releaseField(boolean commit) {
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
        if (this.field.getValue().equals(fieldText(this.value))) return;
        double parsed = parse(this.field.getValue());
        if (!Double.isNaN(parsed)) {
            set(ScaleBounds.isValid(parsed) ? this.range.clamp(parsed) : this.range.min(), false);
        }
        syncField();
    }

    private void set(double next) {
        set(next, true);
    }

    private void set(double next, boolean syncField) {
        boolean changed = !ScaleBounds.same(next, this.value);
        this.value = next;
        if (syncField && !this.field.isFocused()) syncField();
        if (changed && this.soundCooldown == 0 && this.minecraft != null) {
            float pitch = Mth.lerp((float) (offset(next) - offset(BOARD_MIN)) / (offset(BOARD_MAX) - offset(BOARD_MIN)),
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

    private static String fieldText(double scale) {
        if (scale >= 0.001D - ScaleBounds.EPSILON) return String.format(Locale.ROOT, "%.3f", scale);
        return new java.math.BigDecimal(scale).round(new java.math.MathContext(3)).stripTrailingZeros().toPlainString();
    }

    private void showField(String text) {
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
        LocalPlayer player = Minecraft.getInstance().player;
        ItemStack stack = held();
        if (player == null || stack == null) return;
        if (this.field != null && this.field.isFocused()) commitField();

        ScaleSelectingItem tool = (ScaleSelectingItem) stack.getItem();
        if (ScaleBounds.isValid(this.value) && this.range.contains(this.value)) {
            if (stack.getItem() instanceof CreativeShrinkRayItem) {
                CreativeShrinkRayItem.setTargetingMode(stack, this.targeting, player);
            }
            tool.select(stack, this.value);

            if (stack.getItem() instanceof CreativeShrinkRayItem) {
                PacketDistributor.sendToServer(new ShrinkRaySettingsPayload(this.hand, this.value, this.targeting.ordinal()));
            } else {
                PacketDistributor.sendToServer(new ShrinkRayScalePayload(this.hand, this.value));
            }
        }
        if (stack.getItem() instanceof SelfResizeDeviceItem && this.pvpToggleVisible) {
            PersonalLock.setClient(this.unlocked);
            PacketDistributor.sendToServer(new PersonalLockPayload(this.unlocked));
        }
    }

    private ItemStack held() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;
        ItemStack stack = player.getItemInHand(this.hand);
        return stack.getItem() instanceof ScaleSelectingItem ? stack : null;
    }

    private static int offset(double scale) {
        double clamped = Mth.clamp(scale, BOARD_MIN, BOARD_MAX);
        double along = clamped <= ScaleBounds.FULL
                ? log2(clamped) * BELOW_SPACING
                : (clamped - ScaleBounds.FULL) * ABOVE_SPACING;
        return CENTRE + (int) Math.round(along);
    }

    private int xOf(double scale) {
        return this.left + BAR_LEFT + offset(scale);
    }

    private double stopAt(double mouseX) {
        if (this.detents.length == 0) return this.value;
        double barX = this.left + BAR_LEFT;
        boolean past = mouseX > barX + offset(BOARD_MAX) || mouseX < barX + offset(BOARD_MIN);
        if (past) {
            double target = Math.log(valueAt(mouseX));
            int best = 0;
            for (int i = 1; i < this.detents.length; i++) {
                if (Math.abs(Math.log(this.detents[i]) - target) < Math.abs(Math.log(this.detents[best]) - target)) best = i;
            }
            return this.detents[best];
        }
        int best = -1;
        for (int i = 0; i < this.detents.length; i++) {
            double stop = this.detents[i];
            if (stop < BOARD_MIN - ScaleBounds.EPSILON || stop > BOARD_MAX + ScaleBounds.EPSILON) continue;
            if (best < 0 || Math.abs(xOf(stop) - mouseX) < Math.abs(xOf(this.detents[best]) - mouseX)) best = i;
        }
        return best < 0 ? this.value : this.detents[best];
    }

    private double freeAt(double mouseX) {
        double scale = valueAt(mouseX);
        return this.range.clamp(round(scale, scale < 0.1D ? 1000.0D : 100.0D));
    }

    private double valueAt(double mouseX) {
        double barX = this.left + BAR_LEFT;
        double maxX = barX + offset(BOARD_MAX);
        double minX = barX + offset(BOARD_MIN);
        if (mouseX > maxX) return this.range.clamp(BOARD_MAX * Math.pow(2.0D, (mouseX - maxX) / BELOW_SPACING));
        if (mouseX < minX) return this.range.clamp(BOARD_MIN * Math.pow(2.0D, (mouseX - minX) / BELOW_SPACING));
        double along = mouseX - barX - CENTRE;
        double scale = along <= 0.0D
                ? Math.pow(2.0D, along / BELOW_SPACING)
                : ScaleBounds.FULL + along / ABOVE_SPACING;
        return this.range.clamp(Mth.clamp(scale, BOARD_MIN, BOARD_MAX));
    }

    private double nudge(int direction) {
        boolean fine = direction > 0 ? this.value < 0.1D - ScaleBounds.EPSILON : this.value <= 0.1D + ScaleBounds.EPSILON;
        double unit = fine ? 1000.0D : 100.0D;
        double next = round(this.value, unit) + direction / unit;
        return this.range.clamp(round(next, unit));
    }

    private static double round(double scale, double unit) {
        return Math.round(scale * unit) / unit;
    }

    private double step(int direction) {
        return step(this.detents, direction);
    }

    private double step(double[] stops, int direction) {
        if (direction > 0) {
            for (double stop : stops) {
                if (stop > this.value + ScaleBounds.EPSILON) return stop;
            }
            return this.value;
        }
        for (int i = stops.length - 1; i >= 0; i--) {
            if (stops[i] < this.value - ScaleBounds.EPSILON) return stops[i];
        }
        return this.value;
    }

    private boolean overBoard(double mouseX, double mouseY) {
        int barX = this.left + BAR_LEFT;
        int barY = this.top + BAR_TOP;
        return mouseX >= barX - 4 && mouseX <= barX + BAR_WIDTH + 4
                && mouseY >= barY - 5 && mouseY <= barY + BAR_HEIGHT + 5;
    }

    static double parse(String raw) {
        String text = raw.trim().toLowerCase(Locale.ROOT).replace(',', '.').replace(" ", "");
        double factor = 1.0D;
        if (text.endsWith("%")) {
            factor = 0.01D;
            text = text.substring(0, text.length() - 1);
        }
        while (text.endsWith("x") || text.endsWith("×")) text = text.substring(0, text.length() - 1);
        if (text.isEmpty()) return Double.NaN;
        try {
            int slash = text.indexOf('/');
            if (slash < 0) return Double.parseDouble(text) * factor;
            double numerator = Double.parseDouble(text.substring(0, slash));
            double denominator = Double.parseDouble(text.substring(slash + 1));
            return denominator == 0.0D ? Double.NaN : numerator / denominator * factor;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0D);
    }
}
