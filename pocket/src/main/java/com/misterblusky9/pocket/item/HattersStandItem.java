package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.entity.HattersStand;
import com.misterblusky9.pocket.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class HattersStandItem extends Item {
    public HattersStandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() == Direction.DOWN) return InteractionResult.FAIL;

        Level level = context.getLevel();
        BlockPos pos = new BlockPlaceContext(context).getClickedPos();
        ItemStack stack = context.getItemInHand();
        Vec3 at = Vec3.atBottomCenterOf(pos);
        AABB box = ModEntities.HATTERS_STAND.get().getDimensions().makeBoundingBox(at.x, at.y, at.z);
        if (!level.noCollision(null, box) || !level.getEntities(null, box).isEmpty()) return InteractionResult.FAIL;

        if (level instanceof ServerLevel server) {
            HattersStand stand = ModEntities.HATTERS_STAND.get().create(server,
                    EntityType.createDefaultStackConfig(server, stack, context.getPlayer()),
                    pos, MobSpawnType.SPAWN_EGG, true, true);
            if (stand == null) return InteractionResult.FAIL;
            float yaw = Mth.floor((Mth.wrapDegrees(context.getRotation() - 180.0F) + 22.5F) / 45.0F) * 45.0F;
            stand.moveTo(stand.getX(), stand.getY(), stand.getZ(), yaw, 0.0F);
            server.addFreshEntityWithPassengers(stand);
            level.playSound(null, stand.getX(), stand.getY(), stand.getZ(),
                    SoundEvents.ARMOR_STAND_PLACE, SoundSource.BLOCKS, 0.75F, 0.8F);
            stand.gameEvent(GameEvent.ENTITY_PLACE, context.getPlayer());
        }
        stack.shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
