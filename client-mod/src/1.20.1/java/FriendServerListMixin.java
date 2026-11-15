package dev.unbox.client.mixin;
import dev.unbox.client.*;
import net.minecraft.client.gui.screens.multiplayer.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerSelectionList.class)
public abstract class FriendServerListMixin extends net.minecraft.client.gui.components.ObjectSelectionList<ServerSelectionList.Entry> {
 protected FriendServerListMixin(net.minecraft.client.Minecraft mc,int w,int h,int top,int bottom,int row){super(mc,w,h,top,bottom,row);}
 @Shadow @Final private JoinMultiplayerScreen screen;
 @Inject(method="refreshEntries",at=@At("TAIL"))
 private void unboxInvitedWorlds(CallbackInfo ci){if(!(screen instanceof MultiplayerHubScreen hub))return;var list=(ServerSelectionList)(Object)this;var entries=new java.util.ArrayList<ServerSelectionList.Entry>();for(var invite:SocialBridge.availableWorlds())entries.add(new FriendWorldEntry(screen,invite));if(hub.publicAllowed())entries.addAll(list.children());replaceEntries(entries);}
}
