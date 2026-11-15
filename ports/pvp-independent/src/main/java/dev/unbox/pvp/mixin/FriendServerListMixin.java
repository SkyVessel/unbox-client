package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.MultiplayerScreen;
import net.minecraft.client.gui.*;
import java.util.List;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerSelectionList.class)
public class FriendServerListMixin {
 @Shadow @Final private GuiMultiplayer owner;
 @Shadow @Final private List<ServerListEntryNormal> serverListInternet;
 @Shadow @Final private List<ServerListEntryLanDetected> serverListLan;
 @Inject(method="getSize",at=@At("RETURN"),cancellable=true)
 private void unboxSize(CallbackInfoReturnable<Integer> ci){if(owner instanceof MultiplayerScreen)ci.setReturnValue(ci.getReturnValue()+((MultiplayerScreen)owner).invitedCount());}
 @Inject(method="getListEntry",at=@At("HEAD"),cancellable=true)
 private void unboxEntry(int index,CallbackInfoReturnable<GuiListExtended.IGuiListEntry> ci){int n=index-serverListInternet.size()-serverListLan.size()-1;if(owner instanceof MultiplayerScreen&&n>=0&&n<((MultiplayerScreen)owner).invitedCount())ci.setReturnValue(((MultiplayerScreen)owner).invitation(n));}
}
