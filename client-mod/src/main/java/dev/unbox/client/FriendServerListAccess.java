package dev.unbox.client.mixin;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(ServerSelectionList.class)
public interface FriendServerListAccess {
 @Invoker("refreshEntries") void unboxRefresh();
}
