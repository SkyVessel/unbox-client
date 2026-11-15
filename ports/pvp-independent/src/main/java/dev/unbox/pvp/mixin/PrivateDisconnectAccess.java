package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.SocialBridge;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(GuiDisconnected.class)
public interface PrivateDisconnectAccess extends SocialBridge.DisconnectReason {@Accessor("message") IChatComponent unboxReason();}
