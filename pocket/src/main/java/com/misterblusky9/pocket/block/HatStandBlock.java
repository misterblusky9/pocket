package com.misterblusky9.pocket.block;

import com.misterblusky9.pocket.build.BuildHostBlock;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.item.ModDataComponents;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

import java.util.Optional;

public class HatStandBlock extends BuildHostBlock implements IWrenchable {
    public static final MapCodec<HatStandBlock> CODEC = simpleCodec(HatStandBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final Vec3 HEAD_CENTRE = new Vec3(0.5, 9.0 / 16.0, 0.5);
    private static final VoxelShape PLATE = Block.box(2, 0, 2, 14, 1, 14);
    private static final VoxelShape BASE = Shapes.or(PLATE, Block.box(7, 1, 7, 9, 5, 9));
    private static final VoxelShape HEAD = Shapes.or(BASE, Block.box(4, 5, 4, 12, 13, 12));

    public HatStandBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(ACTIVE) ? BASE : HEAD;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return HEAD;
    }

    @Override
    public boolean activatedBy(ItemStack stack) {
        return isHeadwear(stack);
    }

    public static boolean isHeadwear(ItemStack stack) {
        if (stack.isEmpty()) return false;
        EquipmentSlot slot = stack.getEquipmentSlot();
        if (slot == null) {
            Equipable equipable = Equipable.get(stack);
            slot = equipable == null ? null : equipable.getEquipmentSlot();
        }
        return slot == EquipmentSlot.HEAD;
    }

    @Override
    public int scaffoldSize() {
        return 8;
    }

    @Override
    public BlockState scaffold() {
        return ModBlocks.POCKET_DUMMY.get().defaultBlockState();
    }

    @Override
    public Vec3 anchor(BlockState state) {
        return HEAD_CENTRE;
    }

    @Override
    public Quaterniond orientation(BlockState state) {
        Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
        return new Quaterniond().rotateY(Math.toRadians(180.0 - facing.toYRot()));
    }

    @Override
    protected BlockEntityType<? extends BuildHostBlockEntity> blockEntityType() {
        return ModBlockEntities.HAT_STAND.get();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.NAME_TAG)) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name == null || !StringUtil.isValidPlayerName(name.getString())
                || !(level.getBlockEntity(pos) instanceof HatStandBlockEntity stand))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!player.mayBuild()) return ItemInteractionResult.FAIL;
        if (!level.isClientSide) {
            stand.setSkin(new ResolvableProfile(Optional.of(name.getString()), Optional.empty(), new PropertyMap()));
            stack.consume(1, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    public BlockPos standPos(BlockState state, BlockPos pos) {
        return pos;
    }

    public boolean holdsHead(BlockState state) {
        return true;
    }

    public @Nullable BlockHitResult clip(BlockGetter level, Vec3 from, Vec3 to, BlockPos pos, BlockState state,
                                         VoxelShape shape) {
        if (shape.isEmpty() || state.getValue(ACTIVE) || !holdsHead(state)
                || !(level.getBlockEntity(standPos(state, pos)) instanceof HatStandBlockEntity stand))
            return shape.clip(from, to, pos);
        BlockHitResult stem = state.setValue(ACTIVE, true).getShape(level, pos).clip(from, to, pos);
        BlockHitResult head = stand.clipHead(from, to, pos);
        if (stem == null || head == null) return stem == null ? head : stem;
        return from.distanceToSqr(stem.getLocation()) <= from.distanceToSqr(head.getLocation()) ? stem : head;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel) || !(level.getBlockEntity(pos) instanceof HatStandBlockEntity stand)) return;
        stand.setSkinLayer(stack.getOrDefault(ModDataComponents.SKIN_LAYER.get(), 0));
        if (stack.has(DataComponents.PROFILE)) stand.setSkin(stack.get(DataComponents.PROFILE));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (level.getBlockEntity(pos) instanceof HatStandBlockEntity stand) stack.applyComponents(stand.collectComponents());
        return stack;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HatStandBlockEntity(pos, state);
    }
}
