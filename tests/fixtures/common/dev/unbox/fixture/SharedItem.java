package dev.unbox.fixture;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** An actual gameplay item: using the voucher turns it into an emerald on the server. */
public final class SharedItem extends Item {
    public SharedItem(Properties properties){super(properties);}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(!level.isClientSide())player.setItemInHand(hand,new ItemStack(Items.EMERALD));
        return InteractionResult.SUCCESS;
    }
}
