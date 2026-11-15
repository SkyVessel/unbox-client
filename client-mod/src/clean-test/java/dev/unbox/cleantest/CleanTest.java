package dev.unbox.cleantest;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
@Mod(modid="unbox_clean_test",name="Clean Test: Auto Sprint + Fullbright",version="1.0.0",clientSideOnly=true,acceptableRemoteVersions="*",acceptedMinecraftVersions="[1.8.9]")
public final class CleanTest {
 @Mod.EventHandler public void init(FMLInitializationEvent event){ModelOcclusion.register();net.minecraftforge.fml.common.FMLCommonHandler.instance().bus().register(this);System.out.println("[clean-test] Auto Sprint and Fullbright enabled. No Unbox UI, social, skin or feedback modules.");}
 @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){if(event.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();if(mc.thePlayer==null||mc.theWorld==null||mc.currentScreen!=null||!mc.inGameHasFocus)return;
  if(mc.thePlayer.movementInput.moveForward>=.8f&&!mc.thePlayer.isSneaking()&&!mc.thePlayer.isCollidedHorizontally&&!mc.thePlayer.isUsingItem()&&(mc.thePlayer.getFoodStats().getFoodLevel()>6||mc.thePlayer.capabilities.allowFlying))mc.thePlayer.setSprinting(true);
 }
 @SubscribeEvent public void frame(TickEvent.RenderTickEvent event){if(event.phase==TickEvent.Phase.END)ModelOcclusion.idle();}
}
